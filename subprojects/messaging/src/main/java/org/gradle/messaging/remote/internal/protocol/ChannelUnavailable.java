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
package org.gradle.messaging.remote.internal.protocol;

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.MessageOriginator;

public class ChannelUnavailable extends DiscoveryMessage {
    private final String channel;
    private final Address address;

    public ChannelUnavailable(MessageOriginator originator, String group, String channel, Address address) {
        super(originator, group);
        this.channel = channel;
        this.address = address;
    }

    public String getChannel() {
        return channel;
    }

    public Address getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }

        ChannelUnavailable other = (ChannelUnavailable) o;
        return channel.equals(other.channel) && address.equals(other.address);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ channel.hashCode() ^ address.hashCode();
    }

    @Override
    public String toString() {
        return String.format("[ChannelUnavailable channel: %s, address: %s, source: %s]", channel, address, getOriginator());
    }
}
