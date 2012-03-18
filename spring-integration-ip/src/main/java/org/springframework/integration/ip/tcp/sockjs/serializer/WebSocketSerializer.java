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
package org.springframework.integration.ip.tcp.sockjs.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.serializer.Serializer;
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class WebSocketSerializer extends AbstractSockJsDeserializer<SockJsFrame> implements Serializer<String> {

	private final Log logger = LogFactory.getLog(this.getClass());

	private Map<InputStream, StringBuilder> fragments = new ConcurrentHashMap<InputStream, StringBuilder>();

	public void removeFragments(InputStream inputStream) {
		this.fragments.remove(inputStream);
	}

	public void serialize(final String data, OutputStream outputStream)
			throws IOException {
		int lenBytes;
		int payloadLen = 0x80; //masked
		boolean pong = data.startsWith("pong:");
		String theData = pong ? data.substring(5) : data;
		int length = theData.length();
		if (length >= Math.pow(2, 16)) {
			lenBytes = 8;
			payloadLen |= 127;
		}
		else if (length > 125) {
			lenBytes = 2;
			payloadLen |= 126;
		}
		else {
			lenBytes = 0;
			payloadLen |= length;
		}
		int mask = (int) System.currentTimeMillis();
		ByteBuffer buffer = ByteBuffer.allocate(length + 6 + lenBytes);
		if (pong) {
			buffer.put((byte) 0x8a);
		} else {
			// Final fragment; text
			buffer.put((byte) 0x81);
		}
		buffer.put((byte) payloadLen);
		if (lenBytes == 2) {
			buffer.putShort((short) length);
		}
		else if (lenBytes == 8) {
			buffer.putLong(length);
		}

		buffer.putInt(mask);
		byte[] maskBytes = new byte[4];
		buffer.position(buffer.position() - 4);
		buffer.get(maskBytes);
		byte[] bytes = theData.getBytes("UTF-8");
		for (int i = 0; i < bytes.length; i++) {
			buffer.put((byte) (bytes[i] ^ maskBytes[i % 4]));
		}
		outputStream.write(buffer.array());
	}

	public SockJsFrame deserialize(InputStream inputStream) throws IOException {
		int bite;
		if (logger.isDebugEnabled()) {
			logger.debug("Available to read:" + inputStream.available());
		}
		boolean done = false;
		int len = 0;
		int n = 0;
		int m = 0;
		byte[] buffer = null;
		boolean fin = false;
		boolean ping = false;
		boolean pong = false;
		int lenBytes = 0;
		while (!done ) {
			bite = inputStream.read();
//			logger.debug("Read:" + Integer.toHexString(bite));
			if (bite < 0 && n == 0) {
				throw new SoftEndOfStreamException("Stream closed between payloads");
			}
			checkClosure(bite);
			switch (n++) {
			case 0:
				fin = (bite & 0x80) > 0;
				switch (bite) {
				case 0x01:
				case 0x81:
					logger.debug("Text, fin=" + fin);
					break;
				case 0x02:
				case 0x82:
					logger.debug("Binary, fin=" + fin);
					throw new IOException("SockJS doesn't support binary");
				case 0x89:
					ping = true;
					logger.debug("Ping, fin=" + fin);
					break;
				case 0x8a:
					pong = true;
					logger.debug("Pong, fin=" + fin);
					break;
				case 0x88:
					throw new IOException("Connection closed");
				default:
					throw new IOException("Unexpected opcode " + Integer.toHexString(bite));
				}
				break;
			case 1:
				if ((bite & 0x80) > 0) {
					throw new IOException("Illegal: Received masked data from server");
				}
				if (bite < 126) {
					len = bite;
					buffer = new byte[len];
				}
				else if (bite == 126) {
					lenBytes = 2;
				}
				else {
					lenBytes = 8;
				}
				break;
			case 2:
			case 3:
			case 4:
			case 5:
				if (lenBytes > 4 && bite != 0) {
					throw new IOException("Max supported length exceeded");
				}
			case 6:
				if (lenBytes > 3 && (bite & 0x80) > 0) {
					throw new IOException("Max supported length exceeded");
				}
			case 7:
			case 8:
			case 9:
				if (lenBytes-- > 0) {
					len = len << 8 | bite;
					if (lenBytes == 0) {
						buffer = new byte[len];
					}
					break;
				}
			default:
				buffer[m++] = (byte) bite;
				done = m >= len;
			}
		};
		String data =  new String(buffer, "UTF-8");
		if (!fin) {
			StringBuilder builder = this.fragments.get(inputStream);
			if (builder == null) {
				builder = new StringBuilder();
				this.fragments.put(inputStream, builder);
			}
			builder.append(data);
			return null;
		}
		else if (ping) {
			return new SockJsFrame(SockJsFrame.TYPE_PING, data);
		}
		else if (pong) {
			return new SockJsFrame(SockJsFrame.TYPE_PONG, data);
		}
		else {
			StringBuilder builder = this.fragments.get(inputStream);
			if (builder == null) {
				return decodeToFrame(data);
			}
			else {
				builder.append(data).toString();
				this.removeFragments(inputStream);
				return this.decodeToFrame(builder.toString());
			}
		}
	}

	protected void checkClosure(int bite) throws IOException {
		if (bite < 0) {
			logger.debug("Socket closed during message assembly");
			throw new IOException("Socket closed during message assembly");
		}
	}

	public void removeState(InputStream inputStream) {
		super.removeState(inputStream);
		this.removeFragments(inputStream);
	}

}
