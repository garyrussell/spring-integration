/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ip.tcp.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.serializer.Serializer;
import org.springframework.integration.Message;
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;

/**
 * A TcpConnection that uses and underlying {@link SocketChannel}.
 * 
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpNioConnection extends AbstractTcpConnection {

	private final SocketChannel socketChannel;

	private volatile OutputStream channelOutputStream;

	private volatile PipedOutputStream pipedOutputStream;

	private volatile PipedInputStream pipedInputStream;

	private volatile boolean usingDirectBuffers;

	private volatile Executor taskExecutor;

	private volatile ByteBuffer rawBuffer;

	private volatile int maxMessageSize = 60 * 1024;

	private volatile long lastRead;

	private AtomicInteger executionControl = new AtomicInteger();

	private volatile boolean writingToPipe;

	/**
	 * Constructs a TcpNetConnection for the SocketChannel.
	 * @param socketChannel the socketChannel
	 * @param server if true this connection was created as
	 * a result of an incoming request.
	 */
	public TcpNioConnection(SocketChannel socketChannel, boolean server, boolean lookupHost) throws Exception {
		super(socketChannel.socket(), server, lookupHost);
		this.socketChannel = socketChannel;
		this.pipedInputStream = new PipedInputStream();
		this.pipedOutputStream = new PipedOutputStream(this.pipedInputStream);
		this.channelOutputStream = new ChannelOutputStream();
	}

	public void close() {
		doClose();
	}

	private void doClose() {
		if (pipedOutputStream != null) {
			try {
				pipedOutputStream.close();
			} catch (IOException e) {}
		}
		try {
			this.socketChannel.close();
		} catch (Exception e) {}
		super.close();
	}

	public boolean isOpen() {
		return this.socketChannel.isOpen();
	}

	@SuppressWarnings("unchecked")
	public void send(Message<?> message) throws Exception {
		synchronized(this.getMapper()) {
			Object object = this.getMapper().fromMessage(message);
			((Serializer<Object>) this.getSerializer()).serialize(object, this.channelOutputStream);
			this.afterSend(message);
		}
	}

	public Object getPayload() throws Exception {
		return this.getDeserializer().deserialize(pipedInputStream);
	}

	public int getPort() {
		return this.socketChannel.socket().getPort();
	}

	/**
	 * Allocates a ByteBuffer of the requested length using normal or
	 * direct buffers, depending on the usingDirectBuffers field.
	 */
	protected ByteBuffer allocate(int length) {
		ByteBuffer buffer;
		if (this.usingDirectBuffers) {
			buffer = ByteBuffer.allocateDirect(length);
		} else {
			buffer = ByteBuffer.allocate(length);
		}
		return buffer;
	}

	/**
	 * If there is no listener, and this connection is not for single use, 
	 * this method exits. When there is a listener, this method assembles
	 * data into messages by invoking convertAndSend whenever there is 
	 * data in the input Stream. Method exits when a message is complete
	 * and there is no more data; thus freeing the thread to work on other
	 * sockets.
	 */
	public void run() {
		if (logger.isTraceEnabled()) {
			logger.trace(this.getConnectionId() + " Nio message assembler running...");
		}
		try {
			if (this.getListener() == null && !this.isSingleUse()) {
				logger.debug("TcpListener exiting - no listener and not single use");
				return;
			}
			try {
				if (dataAvailable()) {
					Message<?> message = convert();
					if (dataAvailable()) {
						// there is more data in the pipe; run another assembler
						// to assemble the next message, while we send ours
						this.executionControl.incrementAndGet();
						this.taskExecutor.execute(this);
					}
					this.executionControl.decrementAndGet();
					if (message != null) {
						sendToChannel(message);
					}
				} else {
					this.executionControl.decrementAndGet();
				}
			} catch (Exception e) {
				if (logger.isTraceEnabled()) {
					logger.error("Read exception " +
							 this.getConnectionId(), e);
				} else {
					logger.error("Read exception " +
								 this.getConnectionId() + " " +
								 e.getClass().getSimpleName() + 
							     ":" + e.getCause() + ":" + e.getMessage());
				}
				this.closeConnection();
				return;
			}
		} finally {
			logger.trace("Nio message assembler exiting...");
			// Final check in case new data came in and the
			// timing was such that we were the last assembler and
			// a new one wasn't run
			try {
				if (this.isOpen() && dataAvailable()) {
					checkForAssembler();
				}
			} catch (IOException e) {
				logger.error("Exception when checking for assembler", e);
			}
		}
	}

	private boolean dataAvailable() throws IOException {
		return this.pipedInputStream.available() > 0 || writingToPipe;
	}

	/**
	 * Blocks until a complete message has been assembled.
	 * Synchronized to avoid concurrency.
	 * @return The Message or null if no data is available.
	 * @throws IOException
	 */
	private synchronized Message<?> convert() throws Exception {
		if (!dataAvailable()) {
			return null;
		}
		Message<?> message = null;
		try {
			message = this.getMapper().toMessage(this);
		} catch (Exception e) {
			this.closeConnection();
			if (e instanceof SocketTimeoutException && this.isSingleUse()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Closing single use socket after timeout " + this.getConnectionId());
				}
			} else {
				if (!(e instanceof SoftEndOfStreamException)) {
					throw e;
				}
			}
			return null;
		}
		return message;
	}

	private void sendToChannel(Message<?> message) {
		boolean intercepted = false;
		try {
			if (message != null) {
				intercepted = getListener().onMessage(message);
			}
		} catch (Exception e) {
			if (e instanceof NoListenerException) {
				if (this.isSingleUse()) {
					if (logger.isDebugEnabled()) {
						logger.debug("Closing single use channel after inbound message " + this.getConnectionId());
					}
					this.closeConnection();
				}
			} else {
				logger.error("Exception sending meeeage: " + message, e);
			}
		}
		/*
		 * For single use sockets, we close after receipt if we are on the client
		 * side, and the data was not intercepted, 
		 * or the server side has no outbound adapter registered
		 */
		if (this.isSingleUse() && ((!this.isServer() && !intercepted) || (this.isServer() && this.getSender() == null))) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing single use cbannel after inbound message " + this.getConnectionId());
			}
			this.closeConnection();
		}
	}

	private void doRead() throws Exception {
		if (this.rawBuffer == null) {
			this.rawBuffer = allocate(maxMessageSize);
		}

		this.writingToPipe = true;
		try {
			if (this.taskExecutor == null) {
				this.taskExecutor = Executors.newSingleThreadExecutor();
			}
			// If there is no assembler running, start one
			checkForAssembler();
			this.rawBuffer.clear();
			int len = this.socketChannel.read(this.rawBuffer);
			if (len < 0) {
				this.writingToPipe = false;
				this.closeConnection();
			}
			this.rawBuffer.flip();
			if (logger.isDebugEnabled()) {
				logger.debug("Read " + rawBuffer.limit() + " into raw buffer");
			}
			this.pipedOutputStream.write(this.rawBuffer.array(), 0, this.rawBuffer.limit());
			this.pipedOutputStream.flush();
		} finally {
			this.writingToPipe = false;
		}
	}

	private void checkForAssembler() {
		synchronized(this.executionControl) {
			if (this.executionControl.incrementAndGet() <= 1) {
				// only execute run() if we don't already have one running
				this.executionControl.set(1);
				this.taskExecutor.execute(this);
			} else {
				this.executionControl.decrementAndGet();
			}
		}
	}

	/**
	 * Invoked by the factory when there is data to be read.
	 */
	public void readPacket() {
		if (logger.isDebugEnabled()) {
			logger.debug(this.getConnectionId() + " Reading...");
		}
		try {
			doRead();
		} catch (ClosedChannelException cce) {
			if (logger.isDebugEnabled()) {
				logger.debug(this.getConnectionId() + " Channel is closed");
			}
			this.closeConnection();
		} catch (Exception e) {
			logger.error("Exception on Read " + 
					     this.getConnectionId() + " " + 
					     e.getMessage());
			this.closeConnection();
		}
	}

	/**
	 * Close the socket due to timeout.
	 */
	void timeout() {
		this.closeConnection();
	}

	/**
	 * 
	 * @param taskExecutor the taskExecutor to set
	 */
	public void setTaskExecutor(Executor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * If true, connection will attempt to use direct buffers where
	 * possible.
	 * @param usingDirectBuffers
	 */
	public void setUsingDirectBuffers(boolean usingDirectBuffers) {
		this.usingDirectBuffers = usingDirectBuffers;
	}

	/**
	 * 
	 * @return Time of last read.
	 */
	public long getLastRead() {
		return lastRead;
	}

	/**
	 * 
	 * @param lastRead The time of the last read.
	 */
	public void setLastRead(long lastRead) {
		this.lastRead = lastRead;
	}

	public InputStream getInputStream() throws IOException {
		return pipedInputStream;
	}

	/**
	 * OutputStream to wrap a SocketChannel; implements timeout on write. 
	 *
	 */
	class ChannelOutputStream extends OutputStream {

		private Selector selector;

		private int soTimeout;

		@Override
		public void write(int b) throws IOException {
			byte[] bytes = new byte[1];
			bytes[0] = (byte) b;
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			doWrite(buffer);
		}

		@Override
		public void close() throws IOException {
			doClose();
		}

		@Override
		public void flush() throws IOException {
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
			doWrite(buffer);
		}

		@Override
		public void write(byte[] b) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(b);
			doWrite(buffer);
		}

		private synchronized void doWrite(ByteBuffer buffer) throws IOException {
			socketChannel.write(buffer);
			int remaining = buffer.remaining();
			if (remaining == 0) {
				return;
			}
			if (this.selector == null) {
				this.selector = Selector.open();
				this.soTimeout = socketChannel.socket().getSoTimeout();
			}
			socketChannel.register(selector, SelectionKey.OP_WRITE);
			while (remaining > 0) {
				int selectionCount = this.selector.select(this.soTimeout);
				if (selectionCount == 0) {
					throw new SocketTimeoutException("Timeout on write");
				}
				selector.selectedKeys().clear();
				socketChannel.write(buffer);
				remaining = buffer.remaining();
			}
		}

	}

}
