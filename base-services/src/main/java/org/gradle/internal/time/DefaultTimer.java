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

import java.util.concurrent.TimeUnit;

class DefaultTimer implements Timer {

    private final TimeSource timeSource;
    private long startTime;

    DefaultTimer(TimeSource timeSource) {
        this.timeSource = timeSource;
        reset();
    }

    @Override
    public String getElapsed() {
        long elapsedMillis = getElapsedMillis();
        return TimeFormatting.formatDurationVerbose(elapsedMillis);
    }

    @Override
    public long getElapsedMillis() {
        long elapsedNanos = timeSource.nanoTime() - startTime;
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        // System.nanoTime() can go backwards under some circumstances.
        // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6458294
        // This max() call ensures that we don't return negative durations.
        return Math.max(elapsedMillis, 0);
    }

    @Override
    public void reset() {
        startTime = timeSource.nanoTime();
    }

}
