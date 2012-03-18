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

import org.springframework.integration.ip.tcp.serializer.SoftEndOfStreamException;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;
import org.springframework.util.Assert;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class XHRStreamingChunkDeserializer extends AbstractSockJsDeserializer<List<SockJsFrame>> {

	public List<SockJsFrame> deserialize(InputStream inputStream) throws IOException {
		List<SockJsFrame> headers = checkStreaming(inputStream);
		if (headers != null) {
			return headers;
		}
		boolean complete = false;
		StringBuilder builder = new StringBuilder();
		while (!complete) {
			byte[] chunkLengthInHex = this.crlfDeserializer.deserialize(inputStream);
			if (chunkLengthInHex.length == 0) {
				break;
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
		return this.decodeFrameData(builder.toString());
	}

	List<SockJsFrame> decodeFrameData(String frameData) throws IOException {
		List<SockJsFrame> dataList = new ArrayList<SockJsFrame>();
		// some servers put multiple frames in the same chunk
		String[] frames;
		if (frameData.contains("\n")) {
			frames = frameData.split("\n");
		}
		else {
			frames = new String[] {frameData};
		}
		for (String data : frames) {
			dataList.add(this.decodeToFrame(data));
		}
		return dataList;
	}

}
