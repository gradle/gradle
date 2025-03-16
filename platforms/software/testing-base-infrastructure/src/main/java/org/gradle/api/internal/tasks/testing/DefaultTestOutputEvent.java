/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.scan.UsedByScanPlugin;

@UsedByScanPlugin("test-distribution")
public class DefaultTestOutputEvent implements TestOutputEvent {

    private final long logTime;
    private final Destination destination;
    private final String message;

    /**
     * Creates a new test output event.
     *
     * @param destination the destination of the message
     * @param message the message content
     * @deprecated Please provide a log time with {@link #DefaultTestOutputEvent(long, Destination, String)}. It's recommended to use {@link org.gradle.internal.time.Clock} as well.
     */
    @UsedByScanPlugin("test-distribution")
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public DefaultTestOutputEvent(Destination destination, String message) {
        this(System.currentTimeMillis(), destination, message);
    }

    public DefaultTestOutputEvent(long logTime, Destination destination, String message) {
        this.logTime = logTime;
        this.destination = destination;
        this.message = message;
    }

    @Override
    public long getLogTime() {
        return logTime;
    }

    @Override
    public Destination getDestination() {
        return destination;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultTestOutputEvent that = (DefaultTestOutputEvent) o;

        if (logTime != that.logTime) {
            return false;
        }
        if (destination != that.destination) {
            return false;
        }
        if (!message.equals(that.message)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = ((Long) logTime).hashCode();
        result = 31 * result + destination.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }
}
