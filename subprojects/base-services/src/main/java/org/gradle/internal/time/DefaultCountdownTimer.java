/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;

class DefaultCountdownTimer extends DefaultTimer implements CountdownTimer {
    private final long timeoutMillis;

    DefaultCountdownTimer(TimeSource timeSource, long timeout, TimeUnit unit) {
        super(timeSource);
        Preconditions.checkArgument(timeout > 0);
        this.timeoutMillis = unit.toMillis(timeout);
    }

    @Override
    public boolean hasExpired() {
        return getRemainingMillis() <= 0;
    }

    @Override
    public long getRemainingMillis() {
        return Math.max(timeoutMillis - getElapsedMillis(), 0);
    }
}
