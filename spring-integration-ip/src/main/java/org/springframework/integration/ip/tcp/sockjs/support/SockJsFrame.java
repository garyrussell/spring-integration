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
package org.springframework.integration.ip.tcp.sockjs.support;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJsFrame {

	public static final int TYPE_HEADERS = 1;

	public static final int TYPE_HEARTBEAT = 2;

	public static final int TYPE_PRELUDE = 3;

	public static final int TYPE_DATA = 4;

	public static final int TYPE_PING = 5;

	public static final int TYPE_PONG = 6;

	public static final int TYPE_OPEN = 7;

	public static final int TYPE_CLOSE = 8;

	public static final int TYPE_UNEXPECTED = 9;

	public static final int TYPE_COOKIES = 10;

	private static final String[] typeToString = new String[] {
		"Invalid", "Headers", "HeartBeat", "XHR Prelude", "Data", "Ping", "Pong", "Open", "Close", "Unexpected", "Cookies"
	};

	private final int type;

	private final String payload;

	public SockJsFrame(int type, String payload) {
		this.type = type;
		this.payload = payload;
	}

	public int getType() {
		return this.type;
	}

	public String getPayload() {
		return this.payload;
	}

	@Override
	public String toString() {
		return "SockJsFrame [type=" + typeToString[type] + ", payload=" + payload + "]";
	}

}
