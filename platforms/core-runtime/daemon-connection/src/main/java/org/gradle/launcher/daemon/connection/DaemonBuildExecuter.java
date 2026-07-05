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
package org.gradle.launcher.daemon.connection;

import org.jspecify.annotations.NullMarked;

/**
 * Runs a build in a Gradle daemon of the current version and returns its result.
 *
 * <p>This is the connect-and-execute half of the daemon abstraction: given a {@link DaemonBuildRequest}, an
 * implementation locates or starts a suitable daemon, sends the build, streams its output and events back, and
 * returns the {@link DaemonBuildResult}. It is the single seam the daemon build client goes through, and the
 * contract a cross-version or non-JVM client can implement over a different transport. Neither the request nor
 * the result exposes a JVM {@code BuildAction}/{@code BuildActionResult} on this surface.
 */
@NullMarked
public interface DaemonBuildExecuter {
    /**
     * Connects to a daemon, runs the given build to completion, and returns its result.
     */
    DaemonBuildResult execute(DaemonBuildRequest request);
}
