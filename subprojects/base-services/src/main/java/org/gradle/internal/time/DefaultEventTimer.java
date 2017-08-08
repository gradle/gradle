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

public class DefaultEventTimer extends DefaultTimer implements EventTimer {
    private long startTime;

    /**
     * Creates a clock with the specified startTime, in ms since epoch.
     * An attempt is made to correct the startInstant for time elapsed since the specified startTime.
     * However, this correction is susceptible to clock-shift, as well clocks that are not synchronized.
     */
    public DefaultEventTimer(long startTime) {
        super(new TimeSource.True());
        this.startTime = startTime;
        long msSinceStart = Math.max(getWallClockMillis() - startTime, 0);
        this.startInstantMillis = this.startInstantMillis - msSinceStart;
    }

    public DefaultEventTimer() {
        super(new TimeSource.True());
    }

    protected DefaultEventTimer(TimeSource timeProvider) {
        super(timeProvider);
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    public void reset() {
        super.reset();
        startTime = getWallClockMillis();
    }
}
