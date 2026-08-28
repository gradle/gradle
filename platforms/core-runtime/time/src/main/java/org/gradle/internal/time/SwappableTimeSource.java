/*
 * Copyright 2026 the original author or authors.
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

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link TimeSource} delegates to some other time source. Allows the underling time source
 * to be transparently swapped. Intended to be used by {@link TimeSourceManager} to substitute
 * expensive platform timers with a faster timer, on platforms where the platform timer is
 * determined to be expensive.
 */
@ThreadSafe
class SwappableTimeSource implements TimeSource {

    private volatile TimeSource delegate;

    SwappableTimeSource(TimeSource initial) {
        this.delegate = initial;
    }

    void set(TimeSource timeSource) {
        delegate = timeSource;
    }

    TimeSource get() {
        return delegate;
    }

    @Override
    public long currentTimeMillis() {
        return delegate.currentTimeMillis();
    }

    @Override
    public long nanoTime() {
        return delegate.nanoTime();
    }

}
