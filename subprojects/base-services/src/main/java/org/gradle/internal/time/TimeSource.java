/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.time;

/**
 * A source for time values.
 */
public interface TimeSource {
    long currentTimeMillis();
    long nanoTime();

    /**
     * A time source backed by {@link System#currentTimeMillis()} and {@link System#nanoTime()}.
     */
    class True implements TimeSource {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }

    /**
     * A time source with fixed values.
     */
    class Fixed implements TimeSource {
        private final long millis;
        private long nanos;

        public Fixed(long millis, long nanos) {
            this.millis = millis;
            this.nanos = nanos;
        }

        public void setNanoTime(long nanos) {
            this.nanos = nanos;
        }

        @Override
        public long currentTimeMillis() {
            return millis;
        }

        @Override
        public long nanoTime() {
            return nanos;
        }
    }
}
