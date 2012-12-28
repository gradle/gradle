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

import org.gradle.messaging.remote.internal.MessageOriginator;

public class DiscoveryMessage {
    private final MessageOriginator originator;
    private final String group;

    public DiscoveryMessage(MessageOriginator originator, String group) {
        this.originator = originator;
        this.group = group;
    }

    public MessageOriginator getOriginator() {
        return originator;
    }

    public String getGroup() {
        return group;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        DiscoveryMessage other = (DiscoveryMessage) o;
        return originator.equals(other.originator) && group.equals(other.group);
    }

    @Override
    public int hashCode() {
        return group.hashCode();
    }
}
