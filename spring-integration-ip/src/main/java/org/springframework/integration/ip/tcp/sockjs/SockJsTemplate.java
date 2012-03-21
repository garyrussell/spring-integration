/*
 * Copyright 2002-2012 the original author or authors.
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
package org.springframework.integration.ip.tcp.sockjs;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.serializer.Deserializer;
import org.springframework.integration.Message;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.ip.tcp.connection.TcpListener;
import org.springframework.integration.ip.tcp.connection.TcpSender;
import org.springframework.integration.ip.tcp.serializer.StatefulDeserializer;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;
import org.springframework.integration.message.GenericMessage;
import org.springframework.util.Assert;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJsTemplate implements TcpListener, SockJsOperations {

	private final Log logger = LogFactory.getLog(this.getClass());

	private final AbstractClientConnectionFactory connectionFactory;

	private boolean gzipping;

	public SockJsTemplate(AbstractClientConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
		this.connectionFactory.registerListener(this);
	}
	
	boolean isGzipping() {
		return gzipping;
	}

	void setGzipping(boolean gzipping) {
		this.gzipping = gzipping;
	}

	public SockJsContext startStream(String baseResource, final SockJsCallback callback) {
		final String uuid = UUID.randomUUID().toString(); 
		SockJsContext sockJsContext = new SockJsContext(uuid);
		try {
			TcpConnection connection = this.connectionFactory.getNewConnection();
			registerListener(connection, callback, uuid, sockJsContext);
			registerSender(connection, callback, uuid);
			connection.send(new GenericMessage<String>(
				"POST " + baseResource + "/" + uuid + "/xhr_streaming HTTP/1.1\r\n" +
				"Host: " + this.connectionFactory.getHost() + "\r\n" +
				"Connection: keep-alive\r\n" +
				"Accept-Encoding: " + (this.gzipping ? "gzip" : "identity") + "\r\n" +
				"Content-Length: 0\r\n" +
				"\r\n"));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return sockJsContext;
	}

	private void registerSender(TcpConnection connection,
			final SockJsCallback callback, final String uuid) {
		connection.registerSender(new TcpSender() {

			public void addNewConnection(TcpConnection connection) {
			}

			public void removeDeadConnection(TcpConnection connection) {
				callback.closed(uuid);
				Deserializer<?> deserializer = connectionFactory.getDeserializer();
				if (deserializer instanceof StatefulDeserializer) {
					try {
						((StatefulDeserializer<?>) deserializer).removeState(connection.getInputStream());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
	}

	private void registerListener(final TcpConnection connection,
			final SockJsCallback callback, final String uuid, final SockJsContext sockJsContext) {
		connection.registerListener(new TcpListener() {

			@SuppressWarnings("unchecked")
			public boolean onMessage(Message<?> message) {
				for (SockJsFrame frame : (List<SockJsFrame>) message.getPayload()) {
					switch (frame.getType()) {
					case SockJsFrame.TYPE_COOKIES:
						sockJsContext.setCookies(frame.getPayload());
						break;
					case SockJsFrame.TYPE_CLOSE:
						connection.close();
						callback.closed(uuid);
						break;
					case SockJsFrame.TYPE_HEADERS:
					case SockJsFrame.TYPE_HEARTBEAT:
					case SockJsFrame.TYPE_OPEN:
					case SockJsFrame.TYPE_PING:
					case SockJsFrame.TYPE_PONG:
					case SockJsFrame.TYPE_PRELUDE:
					case SockJsFrame.TYPE_UNEXPECTED:
						callback.control(frame, uuid);
						break;
					case SockJsFrame.TYPE_DATA:
						callback.data(frame, uuid);
					}
				}
				return false;
			}
		});
	}

	public boolean onMessage(Message<?> message) {
		logger.error("Should not be called");
		return false;
	}

	public SockJsFrame sendXhr(String baseResource, String uuid, String data, SockJsContext context) {
		try {
			TcpConnection connection = this.connectionFactory.getNewConnection();
			final BlockingQueue<SockJsFrame> reply = new LinkedBlockingQueue<SockJsFrame>();
			connection.registerListener(new TcpListener() {
				public boolean onMessage(Message<?> message) {
					@SuppressWarnings("unchecked")
					SockJsFrame frame = (SockJsFrame) ((List<SockJsFrame>) message.getPayload()).get(0);
					Assert.isTrue(frame.getType() == SockJsFrame.TYPE_HEADERS);
					reply.offer(frame);
					return false;
				}
			});
			connection.send(new GenericMessage<String>(
				"POST " + baseResource + "/" + uuid + "/xhr_send HTTP/1.1\r\n" +
				"Host: " + this.connectionFactory.getHost() + "\r\n" +
				"Accept-Encoding: identity\r\n" +
				context.getCookies() + "\r\n" +
				"Content-Length: " + data.length() + "\r\n" +
				"\r\n" +
				data));
			SockJsFrame frame = reply.poll(10, TimeUnit.SECONDS);
			connection.close();
			return frame;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	
}
