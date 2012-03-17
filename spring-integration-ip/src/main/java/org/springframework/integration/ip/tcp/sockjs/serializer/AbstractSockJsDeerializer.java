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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.integration.ip.tcp.serializer.StatefulDeserializer;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;

/**
 * @author Gary Russell
 * @since 2.1
 *
 */
public abstract class AbstractSockJsDeerializer<T> implements StatefulDeserializer<T> {

	private final Log logger = LogFactory.getLog(this.getClass());

	protected ByteArrayCrLfSerializer crlfDeserializer = new ByteArrayCrLfSerializer();

	private final Map<InputStream, Boolean> streaming = new ConcurrentHashMap<InputStream, Boolean>();

	protected List<SockJsFrame> checkStreaming(InputStream inputStream) throws IOException {
		Boolean isStreaming = this.streaming.get(inputStream);
		if (isStreaming == null) { //Consume the headers - TODO - check status
			StringBuilder builder = new StringBuilder();
			byte[] headers;
			do {
				headers = this.crlfDeserializer.deserialize(inputStream);
				builder.append(new String(headers, "UTF-8")).append("\r\n");
			}
			while (headers.length > 0);
			this.streaming.put(inputStream, Boolean.TRUE);
			return decodeHeaders(builder.toString());
		}
		return null;
	}

	private List<SockJsFrame> decodeHeaders(String frameData) {
		// TODO: Full header separation - mvc utils?
		List<SockJsFrame> dataList = new ArrayList<SockJsFrame>();
		System.out.println("Received:Headers\r\n" + frameData);
		String[] headers = frameData.split("\\r\\n");
		String cookies = "Cookie: ";
		for (String header : headers) {
			if (header.startsWith("Set-Cookie")) {
				String[] bits = header.split(": *");
				cookies += bits[1] + "; ";
			}
		}
		System.out.println(cookies);
		dataList.add(new SockJsFrame(SockJsFrame.TYPE_HEADERS, frameData));
		if (cookies.length() > 8) {
			dataList.add(new SockJsFrame(SockJsFrame.TYPE_COOKIES, cookies));
		}
		return dataList;
	}

	protected void checkClosure(int bite) throws IOException {
		if (bite < 0) {
			logger.debug("Socket closed during message assembly");
			throw new IOException("Socket closed during message assembly");
		}
	}

	public void removeState(InputStream inputStream) {
		this.streaming.remove(inputStream);
	}

	public abstract T deserialize(InputStream inputStream) throws IOException;
	
	protected SockJsFrame decodeToFrame(String data) {
		if (data.length() == 1 && data.equals("h")) {
			System.out.println("Received:SockJS-Heartbeat");
			return new SockJsFrame(SockJsFrame.TYPE_HEARTBEAT, data);
		}
		else if (data.length() == 0x800 && data.startsWith("hhhhhhhhhhhhh")) {
			System.out.println("Received:SockJS-XHR-Prelude");
			return new SockJsFrame(SockJsFrame.TYPE_PRELUDE, data);
		}
		else if (data.length() == 1 && data.equals("o")) {
			System.out.println("Received:SockJS-Open");
			return new SockJsFrame(SockJsFrame.TYPE_OPEN, data);
		}
		else if (data.length() > 0 && data.startsWith("c")) {
			System.out.println("Received SockJS-Close:" + data.substring(1));
			return new SockJsFrame(SockJsFrame.TYPE_CLOSE, data.substring(1));
		}
		else if (data.length() > 0 && data.startsWith("a")) {
			System.out.println("Received data:" + data.substring(1));
			return new SockJsFrame(SockJsFrame.TYPE_DATA, data.substring(1));
		}
		else {
			System.out.println("Received unexpected:" + new String(data));
			return new SockJsFrame(SockJsFrame.TYPE_UNEXPECTED, data);
		}
	}

}