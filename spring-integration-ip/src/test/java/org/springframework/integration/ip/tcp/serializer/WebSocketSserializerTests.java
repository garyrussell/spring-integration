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

import java.io.ByteArrayOutputStream;

import org.junit.Test;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class WebSocketSserializerTests {

	WebSocketSerializer serializer = new WebSocketSerializer();

	@Test
	public void testShortLength() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		String data = "Hello, world!";
		serializer.serialize(data, baos);
		byte[] bytes = baos.toByteArray();
		assertEquals(0x81, (int) bytes[0] & 0xff);
		assertEquals(0x8d, (int) bytes[1] & 0xff);
		int mask = 0;
		for (int i = 6; i < bytes.length; i++) {
			bytes[i] ^= bytes[mask++ % 4 + 2];
		}
		String result = new String(bytes, 6, bytes.length - 6);
		assertEquals(data, result);
	}

	@Test
	public void testMediumLength() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bytes = new byte[150];
		bytes[0] = 'A';
		bytes[149] = 'Z';
		String data = new String(bytes);
		serializer.serialize(data, baos);
		bytes = baos.toByteArray();
		assertEquals(0x81, (int) bytes[0] & 0xff);
		assertEquals(0xFe, (int) bytes[1] & 0xff);
		assertEquals(0, bytes[2]);
		assertEquals(150, (int) bytes[3] & 0xff);
		int mask = 0;
		for (int i = 8; i < bytes.length; i++) {
			bytes[i] ^= bytes[mask++ % 4 + 4];
		}
		String result = new String(bytes, 8, bytes.length - 8);
		assertEquals(data, result);
	}

	@Test
	public void testMediumLengthFull() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bytes = new byte[(int) Math.pow(2, 16) - 1];
		bytes[0] = 'A';
		bytes[(int) Math.pow(2, 16) - 2] = 'Z';
		String data = new String(bytes);
		serializer.serialize(data, baos);
		bytes = baos.toByteArray();
		assertEquals(0x81, (int) bytes[0] & 0xff);
		assertEquals(0xFe, (int) bytes[1] & 0xff);
		assertEquals(0xff, (int) bytes[2] & 0xff);
		assertEquals(0xff, (int) bytes[3] & 0xff);
		int mask = 0;
		for (int i = 8; i < bytes.length; i++) {
			bytes[i] ^= bytes[mask++ % 4 + 4];
		}
		String result = new String(bytes, 8, bytes.length - 8);
		assertEquals(data, result);
	}

	@Test
	public void testLongLength() throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bytes = new byte[(int) Math.pow(2, 16)];
		bytes[0] = 'A';
		bytes[(int) Math.pow(2, 16) - 1] = 'Z';
		String data = new String(bytes);
		serializer.serialize(data, baos);
		bytes = baos.toByteArray();
		assertEquals(0x81, (int) bytes[0] & 0xff);
		assertEquals(0xff, (int) bytes[1] & 0xff);
		assertEquals(0, (int) bytes[2] & 0xff);
		assertEquals(0, (int) bytes[3] & 0xff);
		assertEquals(0, (int) bytes[4] & 0xff);
		assertEquals(0, (int) bytes[5] & 0xff);
		assertEquals(0, (int) bytes[6] & 0xff);
		assertEquals(1, (int) bytes[7] & 0xff);
		assertEquals(0, (int) bytes[8] & 0xff);
		assertEquals(0, (int) bytes[9] & 0xff);
		int mask = 0;
		for (int i = 14; i < bytes.length; i++) {
			bytes[i] ^= bytes[mask++ % 4 + 10];
		}
		String result = new String(bytes, 14, bytes.length - 14);
		assertEquals(data, result);
	}

}
