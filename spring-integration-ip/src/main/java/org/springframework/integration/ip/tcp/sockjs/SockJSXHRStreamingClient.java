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
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;
import org.springframework.integration.ip.tcp.serializer.XHRStreamingChunkDeserializer;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJSXHRStreamingClient {

	private XHRStreamingChunkDeserializer deserializer = new XHRStreamingChunkDeserializer();

	private ByteArrayCrLfSerializer crlfDeserializer = new ByteArrayCrLfSerializer();

	public static void main(String[] args) throws Exception {
		new SockJSXHRStreamingClient().start();
	}

	public void start() throws Exception {
		String uuid = UUID.randomUUID().toString();
		String init =
			"POST /echo/000/" + uuid + "/xhr_streaming HTTP/1.1\r\n" +
			"Accept-Encoding: identity\r\n" +
			"Content-Length: 0\r\n" +
			"\r\n";
		int port = 8081;
		String host = "localhost";
//		String host = "192.168.222.132";
		Socket sock = SocketFactory.getDefault().createSocket(host, port);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(new SocksJSXHRStreamingReader(sock));
		sock.getOutputStream().write(init.getBytes());
		String statusLine;
		do {
			Thread.sleep(1000);
			if (sock.isClosed()) {
				break;
			}
			Socket sender = SocketFactory.getDefault().createSocket(host, port);
			statusLine = send(sender, uuid);
		}
		while (statusLine.equals("HTTP/1.1 204 No Content"));
		sock.close();
		executor.shutdown();
	}

	private String readHeaders(Socket sock) throws IOException {
		String statusLine = new String(this.crlfDeserializer.deserialize(sock.getInputStream()));
		while (true) {
			String s = new String(this.crlfDeserializer.deserialize(sock.getInputStream()));
//			System.out.println(s);
			if (s.length() == 0) {
				break;
			}
		}
//		System.out.println("Read headers");
//		System.out.println(statusLine);
		return statusLine;
	}

	private String send(Socket sock, String uuid) throws IOException {
		String content = "[\"" + new String(new char[128]).replace('\0', 'x') + "\"]";
		String sendData =
			"POST /echo/000/" + uuid + "/xhr_send HTTP/1.1\r\n" +
			"Accept-Encoding: identity\r\n" +
			"Content-Length: " + content.length() + "\r\n" +
			"\r\n" +
			content;
		System.out.println("Sending... " + content);
		sock.getOutputStream().write(sendData.getBytes());
		String statusLine = readHeaders(sock);
		sock.close();
		return statusLine;
	}

	private class SocksJSXHRStreamingReader implements Runnable {

		private Socket sock;

		private SocksJSXHRStreamingReader(Socket sock) {
			this.sock = sock;
		}

		public void run() {
			try {
				readHeaders(this.sock);
				while (true) {
					try {
						String data = deserializer.deserialize(this.sock.getInputStream());
						if (data.length() == 1 && data.equals("h")) {
							System.out.println("Received:SockJS-Heartbeat");
						}
						else if (data.length() == 0x800 && data.startsWith("hhhhhhhhhhhhh")) {
							System.out.println("Received:SockJS-XHR-Prelude");
						}
						else if (data.length() == 1 && data.equals("o")) {
							System.out.println("Received:SockJS-Open");
						}
						else if (data.length() > 0 && data.startsWith("c")) {
							System.out.println("Received SockJS-Close:" + data.substring(1));
							sock.close();
							return;
						}
						else if (data.length() > 0 && data.startsWith("a")) {
							System.out.println("Received data:" + data.substring(1));
						}
						else {
							System.out.println("Received unexpected:" + new String(data));
						}
					}
					catch (SoftEndOfStreamException seose) {
						System.out.println("Stream closed");
						throw new RuntimeException(seose);
					}
					catch (IOException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			}
			catch (RuntimeException re) {
				if (!(re.getCause() instanceof SoftEndOfStreamException)) {
					re.printStackTrace();
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
				try {
					this.sock.close();
				} catch (IOException e1) {
					e1.printStackTrace();
					throw new RuntimeException(e1);
				}
			}
		}
	}

}
