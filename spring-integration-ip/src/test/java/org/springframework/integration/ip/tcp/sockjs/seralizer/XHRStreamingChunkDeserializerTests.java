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
package org.springframework.integration.ip.tcp.sockjs.seralizer;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.Test;
import org.springframework.integration.ip.tcp.sockjs.SockJsFrame;
import org.springframework.integration.ip.tcp.sockjs.serializer.XHRStreamingChunkDeserializer;

/**
 * @author Gary Russell
 * @since 2.1
 *
 */
public class XHRStreamingChunkDeserializerTests {

	@Test
	public void test() throws Exception {
		String test = "HTTP\r\nHeaders\r\n\r\nb\r\naabcdefghi\n\r\n";
		ByteArrayInputStream bais = new ByteArrayInputStream(test.getBytes());
		XHRStreamingChunkDeserializer xhrStreamingChunkDeserializer = new XHRStreamingChunkDeserializer();
		List<SockJsFrame> deserialize = xhrStreamingChunkDeserializer.deserialize(bais);
		assertEquals("HTTP\r\nHeaders\r\n\r\n", deserialize.get(0).getPayload());
		deserialize = xhrStreamingChunkDeserializer.deserialize(bais);
		assertEquals("abcdefghi", deserialize.get(0).getPayload());
	}

}
