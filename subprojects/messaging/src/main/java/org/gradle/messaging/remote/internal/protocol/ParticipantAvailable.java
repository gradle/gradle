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

import org.gradle.messaging.remote.internal.Message;

import java.util.UUID;

public abstract class ParticipantAvailable extends Message implements RouteAvailableMessage {
    private final String channelKey;
    private final UUID id;
    private final String displayName;

    public ParticipantAvailable(UUID id, String displayName, String channelKey) {
        this.id = id;
        this.displayName = displayName;
        this.channelKey = channelKey;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public Object getSource() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("[%s id: %s, displayName: %s, channel: %s]", getClass().getSimpleName(), id, displayName, channelKey);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        ParticipantAvailable other = (ParticipantAvailable) o;
        return id.equals(other.id) && displayName.equals(other.displayName) && channelKey.equals(other.channelKey);
    }

    @Override
    public int hashCode() {
        return id.hashCode() ^ displayName.hashCode() ^ channelKey.hashCode();
    }
}
