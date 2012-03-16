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
package org.springframework.integration.ip.tcp.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class XHRStreamingChunkDeserializer implements StatefulDeserializer<String> {

	private final Log logger = LogFactory.getLog(this.getClass());

	private ByteArrayCrLfSerializer crlfDeserializer = new ByteArrayCrLfSerializer();

	private final Map<InputStream, Boolean> streaming = new ConcurrentHashMap<InputStream, Boolean>();

	public String deserialize(InputStream inputStream) throws IOException {
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
			return builder.toString();
		}
		boolean complete = false;
		StringBuilder builder = new StringBuilder();
		while (!complete) {
			byte[] chunkLengthInHex = this.crlfDeserializer.deserialize(inputStream);
			if (chunkLengthInHex.length == 0) {
				return "";
			}
			int chunkLength = 0;
			try {
				chunkLength = Integer.parseInt(new String(chunkLengthInHex, "UTF-8"), 16);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			if (chunkLength <= 0) {
				throw new SoftEndOfStreamException("0 length chunk received");
			}
			byte[] chunk = new byte[chunkLength];
			for (int i = 0; i < chunkLength; i++) {
				int c = inputStream.read();
				checkClosure(c);
				chunk[i] = (byte) c;
			}
			int eom = chunk[chunkLength-1];
			complete = eom == '\n';
			Assert.isTrue(inputStream.read() == '\r', "Expected \\r");
			Assert.isTrue(inputStream.read() == '\n', "Expected \\n");
			int adjust = complete ? 1 : 0;
			String data = new String(chunk, 0, chunkLength - adjust, "UTF-8");
//			System.out.println(data.length() + ":" + data);
			builder.append(data);
		}
		return builder.toString();
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

}
