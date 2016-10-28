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

package org.gradle.launcher.daemon.server.stats;

import org.gradle.internal.time.Clock;

public class DaemonRunningStats {

    private final Clock daemonClock;
    private Clock currentBuildClock;

    private int buildCount;
    private long allBuildsTime;

    public DaemonRunningStats(Clock daemonClock) {
        this.daemonClock = daemonClock;
    }

    public int getBuildCount() {
        return buildCount;
    }

    public String getPrettyUpTime() {
        return daemonClock.getElapsed();
    }

    public long getStartTime() {
        return daemonClock.getStartTime();
    }

    public long getCurrentBuildStart() {
        return currentBuildClock.getStartTime();
    }

    public long getAllBuildsTime() {
        return allBuildsTime;
    }

    // TODO: these should be moved off to a separate type

    public void buildStarted() {
        ++buildCount;
        currentBuildClock = new Clock();
    }

    public void buildFinished() {
        long buildTime = Math.max(currentBuildClock.getElapsedMillis(), 1);
        allBuildsTime += buildTime;
    }
}
