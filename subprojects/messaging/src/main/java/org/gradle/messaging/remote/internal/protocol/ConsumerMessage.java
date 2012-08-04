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

public abstract class ConsumerMessage extends Message implements RoutableMessage {
    protected final UUID consumerId;
    protected final Object producerId;

    public ConsumerMessage(UUID consumerId, Object producerId) {
        this.producerId = producerId;
        this.consumerId = consumerId;
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public Object getProducerId() {
        return producerId;
    }

    public Object getDestination() {
        return producerId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        ConsumerMessage other = (ConsumerMessage) o;
        return consumerId.equals(other.consumerId) && producerId.equals(other.producerId);
    }

    @Override
    public int hashCode() {
        return consumerId.hashCode() ^ producerId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("[%s, consumerId: %s, producerId: %s]", getClass().getSimpleName(), consumerId, producerId);
    }
}
