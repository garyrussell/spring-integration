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
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;

import org.springframework.integration.ip.tcp.serializer.WebSocketSerializer;

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
		Executors.newSingleThreadExecutor().execute(new SocksJSWebSocketReader(sock));
		while(true) {
			Thread.sleep(10000);
			send(sock, "Hello, world!");
		}
	}

	private void send(Socket sock, String string) throws IOException {
		String data = "[\"" + string + "\"]";
		doSend(sock, data);
	}

	private void doSend(Socket sock, String data) throws IOException {
		System.out.println("Sending... " + data);
		this.serializer.serialize(data, sock.getOutputStream());
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

	private class SocksJSWebSocketReader implements Runnable {

		private Socket sock;

		private SocksJSWebSocketReader(Socket sock) {
			this.sock = sock;
		}

		public void run() {
			while (true) {
				try {
					String data = serializer.deserialize(this.sock.getInputStream());
					if (data.length() == 1 && data.equals("h")) {
						System.out.println("Received:SockJS-Heartbeat");
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
					else if (data.length() > 4 && data.startsWith("ping:")) {
						System.out.println("Received ping:" + data.substring(5));
						sendPong(data.substring(5));
					}
					else if (data.length() > 4 && data.startsWith("pong:")) {
						System.out.println("Received pong:" + data.substring(5));
					}
					else if (data.length() == 0) {
						System.out.println("No data received - fragment?");
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

		private void sendPong(String data) throws IOException {
			doSend(sock, "pong:" + data);
		}
	}

}
