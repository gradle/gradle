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

class DefaultClockSync implements ClockSync {

    private final TimeSource timeSource;
    private final HotSwappableClock delegate;

    DefaultClockSync(TimeSource timeSource) {
        this.timeSource = timeSource;
        this.delegate = new HotSwappableClock(new MonotonicElapsedTimeClock(timeSource));
        sync();
    }

    @Override
    public Clock getClock() {
        return delegate;
    }

    @Override
    public long sync() {
        MonotonicElapsedTimeClock next = new MonotonicElapsedTimeClock(timeSource);
        delegate.set(next);
        return next.getStartTime();
    }

}
