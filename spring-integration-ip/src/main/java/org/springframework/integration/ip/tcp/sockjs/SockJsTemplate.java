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
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.serializer.Deserializer;
import org.springframework.integration.Message;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.ip.tcp.connection.TcpListener;
import org.springframework.integration.ip.tcp.connection.TcpSender;
import org.springframework.integration.ip.tcp.serializer.StatefulDeserializer;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class SockJsTemplate implements TcpListener {

	private final Log logger = LogFactory.getLog(this.getClass());

	private final AbstractClientConnectionFactory connectionFactory;

	public SockJsTemplate(AbstractClientConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
		this.connectionFactory.registerListener(this);
	}
	
	public String startStream(String baseUrl, final SockJsCallback callback) {
		final String uuid = UUID.randomUUID().toString(); 
		try {
			TcpConnection connection = this.connectionFactory.getConnection();
			registerListener(connection, callback, uuid);
			registerSender(connection, callback, uuid);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return uuid;
	}

	private void registerSender(TcpConnection connection,
			final SockJsCallback callback, final String uuid) {
		connection.registerSender(new TcpSender() {

			public void addNewConnection(TcpConnection connection) {
			}

			public void removeDeadConnection(TcpConnection connection) {
				callback.closed(uuid);
				Deserializer<?> deserializer = connectionFactory.getDeserializer();
				if (deserializer instanceof StatefulDeserializer) {
					try {
						((StatefulDeserializer<?>) deserializer).removeState(connection.getInputStream());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
	}

	private void registerListener(TcpConnection connection,
			final SockJsCallback callback, final String uuid) {
		connection.registerListener(new TcpListener() {

			public boolean onMessage(Message<?> message) {
				if (message.getHeaders().get("js_control") == null) {
					callback.data((String) message.getPayload(), uuid);
				} else {
					callback.control((String) message.getPayload(), uuid);
				}
				return false;
			}
		});
	}

	public boolean onMessage(Message<?> message) {
		logger.error("Should nor be called");
		return false;
	}

	
}
