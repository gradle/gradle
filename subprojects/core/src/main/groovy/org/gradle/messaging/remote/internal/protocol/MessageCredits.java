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

public class MessageCredits extends Message {
    private final int credits;

    public MessageCredits(int credits) {
        this.credits = credits;
    }

    public int getCredits() {
        return credits;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        MessageCredits other = (MessageCredits) o;
        return credits == other.credits;
    }

    @Override
    public int hashCode() {
        return credits;
    }

    @Override
    public String toString() {
        return String.format("[MessageCredits credits: %d]", credits);
    }
}
