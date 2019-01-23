/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.integration.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * @author Gary Russell
 * @since 5.2
 *
 */
@SpringJUnitConfig
@DirtiesContext
public class MultiInputChannelsTests {

	@Test
	public void test(@Autowired Config config) {
		config.in1().send(new GenericMessage<>("foo"));
		config.in2().send(new GenericMessage<>("bar"));
		assertThat(config.count.get()).isEqualTo(2);
	}

	@Configuration
	public static class Config {

		private final AtomicInteger count = new AtomicInteger();

		@Bean("foo.bar")
		public MessageChannel in1() {
			return new DirectChannel();
		}

		@Bean("foo.baz")
		public MessageChannel in2() {
			return new DirectChannel();
		}

		@Bean
		public EventDrivenConsumer consumer() {
			EventDrivenConsumer consumer = new EventDrivenConsumer(m -> this.count.incrementAndGet());
			consumer.setInputChannelPatterns("foo.*");
			return consumer;
		}

	}

}
