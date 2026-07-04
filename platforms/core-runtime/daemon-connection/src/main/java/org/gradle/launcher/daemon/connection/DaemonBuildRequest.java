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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * A request to run a build in a daemon, independent of <em>how</em> the build is described. There are two
 * shapes:
 *
 * <ul>
 *     <li>{@link JvmBuildRequest} - an in-JVM {@code BuildAction} (what the CLI and Tooling API produce today);
 *     <li>{@link ArgsBuildRequest} - a command line the daemon parses (what a non-JVM client can produce).
 * </ul>
 *
 * The build action itself never appears on this interface, so the contract stays constructable by a client that
 * cannot build a JVM {@code BuildAction}. The cancellation token and tooling-event consumer are collaborators
 * supplied on the daemon side (a native client's wire signals are adapted into them), so they are common to
 * both shapes.
 */
@NullMarked
public interface DaemonBuildRequest {

    /**
     * The token used to cancel the running build.
     */
    BuildCancellationToken getCancellationToken();

    /**
     * The consumer for tooling/build events produced during the build.
     */
    BuildEventConsumer getEventConsumer();
}
