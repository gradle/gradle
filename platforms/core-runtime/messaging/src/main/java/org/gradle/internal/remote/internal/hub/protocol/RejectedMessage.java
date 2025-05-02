/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal.hub.protocol;

public class RejectedMessage extends InterHubMessage implements Routable {
    private final ChannelIdentifier channel;
    private final Object payload;

    public RejectedMessage(ChannelIdentifier channel, Object payload) {
        this.channel = channel;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return String.format("[%s channel:%s, payload:%s]", getClass().getSimpleName(), channel, payload);
    }

    @Override
    public Delivery getDelivery() {
        return Delivery.AllHandlers;
    }

    @Override
    public ChannelIdentifier getChannel() {
        return channel;
    }

    public Object getPayload() {
        return payload;
    }
}
