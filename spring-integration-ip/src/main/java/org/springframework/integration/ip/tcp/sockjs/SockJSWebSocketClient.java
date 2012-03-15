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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import org.springframework.integration.ip.tcp.serializer.AbstractByteArraySerializer;
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJSWebSocketClient {

	private WebSocketSerializer serializer = new WebSocketSerializer();

	public static void main(String[] args) throws Exception {
		new SockJSWebSocketClient().start();
	}

	public void start() throws Exception {
		String init =
			"GET /echo/517/p8e90wok/websocket HTTP/1.1\r\n" +
			"Upgrade: websocket\r\n" +
			"Connection: Upgrade\r\n" +
			"Host: localhost:9999\r\n" +
			"Origin: http://localhost:9999\r\n" +
			"Sec-WebSocket-Key: nGahJFI1wv1Vn/QW5TdFvg==\r\n" +
			"Sec-WebSocket-Version: 13\r\n\r\n";
		Socket sock = SocketFactory.getDefault().createSocket("localhost", 9999);
		sock.getOutputStream().write(init.getBytes());
		handleUpgrade(sock);
		Executors.newSingleThreadExecutor().execute(new WebSocketReader(sock));
		while(true) {
			Thread.sleep(10000);
			send(sock, "Hello, world!");
		}
	}

	private void send(Socket sock, String string) throws IOException {
		String data = "[\"" + string + "\"]";
		System.out.println("Sending... " + data);
		serializer.serialize(data.getBytes(), sock.getOutputStream());
	}

	private void handleUpgrade(Socket sock) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		while (true) {
			String s = reader.readLine();
			System.out.println(s);
			if (s.length() == 0) {
				break;
			}
		}
	}

	private class WebSocketReader implements Runnable {

		private Socket sock;

		private WebSocketReader(Socket sock) {
			this.sock = sock;
		}

		public void run() {
			while (true) {
				try {
					byte[] data = serializer.deserialize(this.sock.getInputStream());
					if (data.length == 1 && data[0] == 'h') {
						System.out.println("Received:SockJS-Heartbeat");
					}
					else if (data.length == 1 && data[0] == 'o') {
						System.out.println("Received:SockJS-Open");
					}
					else if (data.length > 0 && data[0] == 'a'){
						System.out.println("Received data:" + new String(data, 1, data.length-1));
					}
					else {
						System.out.println("Received unexpected:" + new String(data));
					}
				} catch (IOException e) {
					e.printStackTrace();
					try {
						this.sock.close();
					} catch (IOException e1) {
						e1.printStackTrace();
						throw new RuntimeException(e1);
					}
					throw new RuntimeException(e);
				}
			}
		}
	}

	private class WebSocketSerializer extends AbstractByteArraySerializer {

		public void serialize(byte[] data, OutputStream outputStream)
				throws IOException {
			if (data.length > 125) {
				throw new IOException("Currently only support short <126 data");
			}
			int mask = (int) System.currentTimeMillis();
			ByteBuffer buffer = ByteBuffer.allocate(data.length + 6);
			// Final fragment; text
			buffer.put((byte) 0x81);
			buffer.put((byte) (data.length | 0x80));

			buffer.putInt(mask);
			byte[] maskBytes = new byte[4];
			buffer.position(buffer.position() - 4);
			buffer.get(maskBytes);
			for (int i = 0; i < data.length; i++) {
				buffer.put((byte) (data[i] ^ maskBytes[i % 4]));
			}
			buffer.flip();
			outputStream.write(buffer.array());
		}

		public byte[] deserialize(InputStream inputStream) throws IOException {
			byte[] buffer = new byte[this.maxMessageSize];
			int n = 0;
			int bite;
			if (logger.isDebugEnabled()) {
				logger.debug("Available to read:" + inputStream.available());
			}
			boolean done = false;
			int len = 0;
			int m = 0;
			while (!done ) {
				bite = inputStream.read();
				logger.debug("Read:" + Integer.toHexString(bite));
				if (bite < 0 && n == 0) {
					throw new SoftEndOfStreamException("Stream closed between payloads");
				}
				checkClosure(bite);
				switch (n++) {
				case 0:
					if (bite == 0x88) {
						throw new IOException("Connection closed");
					}
					if (bite != 0x81) {
						throw new IOException("Currently only support unfragmented text");
					}
					break;
				case 1:
					if (bite > 125) {
						throw new IOException("Currently only support short <126 data");
					}
					len = bite;
					break;
				default:
					buffer[m++] = (byte) bite;
					done = m >= len;
				}
				if (m >= this.maxMessageSize) {
					throw new IOException("CRLF not found before max message length: "
							+ this.maxMessageSize);
				}
			};
			byte[] assembledData = new byte[m];
			System.arraycopy(buffer, 0, assembledData, 0, m);
			return assembledData;
		}

	}
}
