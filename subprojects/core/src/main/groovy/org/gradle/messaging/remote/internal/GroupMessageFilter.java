/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage;
import org.gradle.messaging.remote.internal.protocol.UnknownMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters messages for unknown groups.
 */
public class GroupMessageFilter implements Dispatch<DiscoveryMessage> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupMessageFilter.class);
    private final Dispatch<? super DiscoveryMessage> dispatch;
    private final String group;

    public GroupMessageFilter(String group, Dispatch<? super DiscoveryMessage> dispatch) {
        this.dispatch = dispatch;
        this.group = group;
    }

    public void dispatch(DiscoveryMessage message) {
        if (message instanceof UnknownMessage) {
            LOGGER.debug("Discarding unknown message {}.", message);
            return;
        }
        if (!message.getGroup().equals(group)) {
            LOGGER.debug("Discarding message {} from unknown group {}.", message, message.getGroup());
            return;
        }
        dispatch.dispatch(message);
    }
}
