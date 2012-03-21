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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.springframework.integration.ip.tcp.sockjs.serializer.WebSocketSerializer;
import org.springframework.integration.ip.tcp.sockjs.support.SockJsFrame;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class WebSocketDeserializerTests {

	WebSocketSerializer deserializer = new WebSocketSerializer();

	@Test
	public void testShortLength() throws Exception {
		byte[] msg = new byte[] {(byte) 0x81, 0x01, 'A'};
		InputStream is = new ByteArrayInputStream(msg);
		SockJsFrame result = deserializer.deserialize(is);
		assertEquals("A", result.getPayload());
	}

	@Test
	public void testMediumLength() throws Exception {
		byte[] msg = new byte[132];
		msg[0] = (byte) 0x81;
		msg[1] = 0x7e;
		msg[2] = 0;
		msg[3] = (byte) 0x80;
		msg[4] = 'A';
		msg[131] = 'Z';
		InputStream is = new ByteArrayInputStream(msg);
		String result = deserializer.deserialize(is).getPayload();
		assertEquals(128, result.length());
		assertTrue(result.startsWith("A"));
		assertTrue(result.endsWith("Z"));
	}

	@Test
	public void testMediumLengthFull() throws Exception {
		byte[] msg = new byte[(int) (Math.pow(2, 16) + 3)];
		msg[0] = (byte) 0x81;
		msg[1] = 0x7e;
		msg[2] = (byte) 0xff;
		msg[3] = (byte) 0xff;
		msg[4] = 'A';
		msg[(int) (Math.pow(2, 16) + 2)] = 'Z';
		InputStream is = new ByteArrayInputStream(msg);
		String result = deserializer.deserialize(is).getPayload();
		assertEquals((int) Math.pow(2, 16) - 1, result.length());
		assertTrue(result.startsWith("A"));
		assertTrue(result.endsWith("Z"));
	}

	@Test
	public void testLongLength() throws Exception {
		byte[] msg = new byte[(int) (Math.pow(2, 16) + 10)];
		msg[0] = (byte) 0x81;
		msg[1] = 0x7f;
		msg[2] = 0;
		msg[3] = 0;
		msg[4] = 0;
		msg[5] = 0;
		msg[6] = 0;
		msg[7] = 1;
		msg[8] = 0;
		msg[9] = 0;
		msg[10] = 'A';
		msg[(int) (Math.pow(2, 16) + 9)] = 'Z';
		InputStream is = new ByteArrayInputStream(msg);
		String result = deserializer.deserialize(is).getPayload();
		assertEquals((int) Math.pow(2, 16), result.length());
		assertTrue(result.startsWith("A"));
		assertTrue(result.endsWith("Z"));
	}

	@Test(expected=IOException.class)
	public void testTooLong() throws Exception {
		byte[] msg = new byte[10];
		msg[0] = (byte) 0x81;
		msg[1] = 0x7f;
		msg[2] = 0;
		msg[3] = 0;
		msg[4] = 1;
		msg[5] = 0;
		msg[6] = 0;
		msg[7] = 1;
		msg[8] = 0;
		msg[9] = 0;
		InputStream is = new ByteArrayInputStream(msg);
		deserializer.deserialize(is);
	}

	@Test
	public void testFragmented() throws Exception {
		byte[] msg = new byte[] {(byte) 0x01, 0x01, 'A'};
		InputStream is = new ByteArrayInputStream(msg);
		SockJsFrame frame = deserializer.deserialize(is);
		assertNull(frame);
		msg[0] = (byte) 0x81;
		msg[1] = 0x01;
		msg[2] = 'B';
		is.reset();
		frame = deserializer.deserialize(is);
		assertEquals("AB", frame.getPayload());
	}
}
