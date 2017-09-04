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

package org.gradle.internal.buildevents;

import org.gradle.internal.time.DefaultEventTimer;
import org.gradle.internal.time.EventTimer;
import org.gradle.internal.time.MonotonicClock;

/**
 * A timer for the current build execution.
 *
 * This is primarily used to provide user feedback on how long the “build” took (see BuildResultLogger).
 *
 * The build timer applies to an entire build tree.
 * For a multiple build build session, each build has its own timer.
 * Builds launched with the GradleBuild task have their own build timer.
 *
 * The timer is considered to have started as soon as the user, or some tool, initiated the build.
 * During continuous build, subsequent builds are timed from when changes are noticed.
 */
public class BuildExecutionTimer implements EventTimer {

    private final EventTimer eventTimer;

    public static BuildExecutionTimer startingNow() {
        return new BuildExecutionTimer(new DefaultEventTimer(MonotonicClock.global().getCurrentTime()));
    }

    public BuildExecutionTimer(EventTimer eventTimer) {
        this.eventTimer = eventTimer;
    }

    @Override
    public long getStartTime() {
        return eventTimer.getStartTime();
    }

    @Override
    public String getElapsed() {
        return eventTimer.getElapsed();
    }

    @Override
    public long getElapsedMillis() {
        return eventTimer.getElapsedMillis();
    }

    @Override
    public void reset() {
        eventTimer.reset();
    }

}
