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

package org.gradle.launcher.daemon.management;

import org.jspecify.annotations.Nullable;

/**
 * What a daemon is doing, and how much that answer can be trusted.
 */
public final class DaemonStatus {

    /**
     * Where the state came from. A caller that shows this to a user should say which, because a recorded
     * state can lag what the daemon is actually doing.
     */
    public enum Source {
        /**
         * The daemon was asked and answered.
         */
        LIVE,
        /**
         * The daemon is reachable but could not be asked, because it runs a Gradle version whose replies
         * this reader cannot decode. The state is the one the daemon last wrote down for itself.
         */
        RECORDED,
        /**
         * The daemon did not answer at all. It may have died without cleaning up after itself.
         */
        UNREACHABLE
    }

    private final String gradleVersion;
    private final @Nullable Long pid;
    private final String state;
    private final Source source;

    public DaemonStatus(String gradleVersion, @Nullable Long pid, String state, Source source) {
        this.gradleVersion = gradleVersion;
        this.pid = pid;
        this.state = state;
        this.source = source;
    }

    public String getGradleVersion() {
        return gradleVersion;
    }

    public @Nullable Long getPid() {
        return pid;
    }

    /**
     * The daemon state, upper cased, matching what {@code gradle --status} has always printed.
     */
    public String getState() {
        return state;
    }

    public Source getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "DaemonStatus{version=" + gradleVersion + ", pid=" + pid + ", state=" + state + ", source=" + source + "}";
    }
}
