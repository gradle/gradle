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
 * The outcome of running a build in a daemon, on the transport-agnostic surface: whether it succeeded and
 * whether it was cancelled. The build-specific result payload (the JVM {@code BuildActionResult}) is carried by
 * {@link JvmBuildResult} for in-JVM callers to re-hydrate; a non-JVM client uses only these flags plus the
 * streamed events.
 */
@NullMarked
public interface DaemonBuildResult {

    /**
     * Whether the build completed without failure.
     */
    boolean isSuccessful();

    /**
     * Whether the build was cancelled.
     */
    boolean wasCancelled();
}
