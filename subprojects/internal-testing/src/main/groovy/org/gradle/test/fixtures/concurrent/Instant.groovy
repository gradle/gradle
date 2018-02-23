/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.fixtures.concurrent

/**
 * A point in time.
 */
class Instant implements Comparable<Instant> {
    final long nanos

    Instant(long nanos) {
        this.nanos = nanos
    }

    @Override
    String toString() {
        return "[instant at $nanos]"
    }

    int compareTo(Instant t) {
        return nanos.compareTo(t.nanos)
    }

    Instant plus(long millis) {
        return new Instant(nanos + millis * 1000)
    }

    Duration minus(Instant t) {
        return new Duration(nanos - t.nanos)
    }
}
