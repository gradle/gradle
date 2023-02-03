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

/**
 * The holder for when the build is considered to have started.
 *
 * This is primarily used to provide user feedback on how long the “build” took (see BuildResultLogger).
 *
 * The build is considered to have started as soon as the user, or some tool, initiated the build.
 * During continuous build, subsequent builds are timed from when changes are noticed.
 */
public class BuildStartedTime {

    private volatile long startTime;

    public static BuildStartedTime startingAt(long startTime) {
        return new BuildStartedTime(startTime);
    }

    public BuildStartedTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void reset(long startTime) {
        this.startTime = startTime;
    }

}
