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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNetClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNioClientConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayRawSerializer;
import org.springframework.integration.ip.tcp.sockjs.serializer.XHRStreamingChunkDeserializer;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJsTemplateTests {

	@Test
	public void testXHRStream() throws Exception {
		AbstractClientConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", 8081);
//		AbstractClientConnectionFactory ccf = new TcpNetClientConnectionFactory("echo-test.cloudfoundry.com", 80);
		testXHSStreamGuts(ccf);
	}

	@Test
	public void testXHRStreamNIO() throws Exception {
		AbstractClientConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", 8081);
		testXHSStreamGuts(ccf);
	}

	private void testXHSStreamGuts(AbstractClientConnectionFactory ccf) throws Exception {
		ccf.setDeserializer(new XHRStreamingChunkDeserializer());
		ccf.setSerializer(new ByteArrayRawSerializer());
		ccf.setSoTimeout(60000);
		SockJsOperations template = new SockJsTemplate(ccf);
		ccf.start();
		final List<String> uuids = new ArrayList<String>();
		final List<String> results = new ArrayList<String>();
		
		SockJsContext context = template.startStream("/echo/000", new SockJsCallback() {
			
			public void data(SockJsFrame frame, String uuid) {
				System.out.println("Received data: " + frame);
				results.add(frame.getPayload());
			}
			
			public void control(SockJsFrame frame, String uuid) {
				System.out.println("Received control: " + frame);
				if (frame.getType() == SockJsFrame.TYPE_OPEN) {
					uuids.add(uuid);
				}
			}
			
			public void closed(String uuid) {
				System.out.println("Closed");
			}
		});
		
		int n = 0;
		while (context.getCookies() == null) {
			Thread.sleep(100);
			if (n++ > 100) {
				fail("Failed to receive cookies");
			}
		}
		for (int i = 0; i < 4; i++) {
			Thread.sleep(1000);
			SockJsFrame frame = template.sendXhr("/echo/000", uuids.get(0),
					"[\"" + new String(new char[128]).replace('\0', 'x') +
					"\"]", context);
			System.out.println(frame.getPayload());
		}
		assertEquals(4, results.size());
		while (results.size() > 0) {
			assertEquals("[\"" + new String(new char[128]).replace('\0', 'x') + "\"]", results.remove(0));
		}
	}

}
