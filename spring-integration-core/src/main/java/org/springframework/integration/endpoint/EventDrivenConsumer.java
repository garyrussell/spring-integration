/*
 * Copyright 2002-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.Lifecycle;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.router.MessageRouter;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.lang.Nullable;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * Message Endpoint that connects any {@link MessageHandler} implementation to a {@link SubscribableChannel}.
 *
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 */
public class EventDrivenConsumer extends AbstractEndpoint implements IntegrationConsumer, SmartInitializingSingleton {

	private final List<SubscribableChannel> inputChannels = new ArrayList<>();

	private final MessageHandler handler;

	private final List<String> inputChannelPatterns = new ArrayList<>();

	public EventDrivenConsumer(MessageHandler handler) {
		this(null, handler);
	}

	public EventDrivenConsumer(@Nullable SubscribableChannel inputChannel, MessageHandler handler) {
		Assert.notNull(handler, "handler must not be null");
		if (inputChannel != null) {
			this.inputChannels.add(inputChannel);
		}
		this.handler = handler;
		this.setPhase(Integer.MIN_VALUE);
	}

	@Override
	public MessageChannel getInputChannel() {
		return this.inputChannels.get(0);
	}

	public List<MessageChannel> getInputChannels() {
		return Collections.unmodifiableList(this.inputChannels);
	}

	@Override
	public MessageChannel getOutputChannel() {
		if (this.handler instanceof MessageProducer) {
			return ((MessageProducer) this.handler).getOutputChannel();
		}
		else if (this.handler instanceof MessageRouter) {
			return ((MessageRouter) this.handler).getDefaultOutputChannel();
		}
		else {
			return null;
		}
	}

	@Override
	public MessageHandler getHandler() {
		return this.handler;
	}

	public void setInputChannelPatterns(String... inputChannelPatterns) {
		Assert.notNull(inputChannelPatterns, "'inputChannelPatterns' must not be null");
		this.inputChannelPatterns.addAll(Arrays.asList(inputChannelPatterns));
	}

	@Override
	public void afterSingletonsInstantiated() {
		Assert.state(ObjectUtils.isEmpty(this.inputChannelPatterns) || this.inputChannels.isEmpty(),
				"Cannot provide an input channel when inputchannel patterns are provided");
		Map<String, SubscribableChannel> channels = getApplicationContext().getBeansOfType(SubscribableChannel.class,
				false, false);
		this.inputChannels.addAll(channels.entrySet()
			.stream()
			.filter(entry -> matches(entry.getKey()))
			.map(entry -> entry.getValue())
			.collect(Collectors.toList()));
	}

	private boolean matches(String beanName) {
		return this.inputChannelPatterns.stream().anyMatch(p -> PatternMatchUtils.simpleMatch(p, beanName));
	}

	@Override
	protected void doStart() {
		this.inputChannels.forEach(c -> {
			logComponentSubscriptionEvent(c, true);
			c.subscribe(this.handler);
		});
		if (this.handler instanceof Lifecycle) {
			((Lifecycle) this.handler).start();
		}
	}

	@Override
	protected void doStop() {
		this.inputChannels.forEach(c -> {
			logComponentSubscriptionEvent(c, false);
			c.unsubscribe(this.handler);
		});
		if (this.handler instanceof Lifecycle) {
			((Lifecycle) this.handler).stop();
		}
	}

	private void logComponentSubscriptionEvent(MessageChannel inputChannel, boolean add) {
		if (this.handler instanceof NamedComponent && inputChannel instanceof NamedComponent) {
			String channelName = ((NamedComponent) inputChannel).getComponentName();
			String componentType = ((NamedComponent) this.handler).getComponentType();
			componentType = StringUtils.hasText(componentType) ? componentType : "";
			String componentName = getComponentName();
			componentName = (StringUtils.hasText(componentName) && componentName.contains("#")) ? "" : ":" + componentName;
			StringBuffer buffer = new StringBuffer();
			buffer.append("{" + componentType + componentName + "} as a subscriber to the '" + channelName + "' channel");
			if (add) {
				buffer.insert(0, "Adding ");
			}
			else {
				buffer.insert(0, "Removing ");
			}
			logger.info(buffer.toString());
		}
	}

}
