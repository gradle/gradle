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

public class ConsumerUnavailable extends Message implements RoutableMessage, RouteUnavailableMessage {
    private final Object id;

    public ConsumerUnavailable(Object id) {
        this.id = id;
    }

    public Object getId() {
        return id;
    }

    public Object getSource() {
        return id;
    }

    public Object getDestination() {
        return null;
    }

    @Override
    public String toString() {
        return String.format("[ConsumerUnavailable id: %s]", id);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        ConsumerUnavailable other = (ConsumerUnavailable) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
