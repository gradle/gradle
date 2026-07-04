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

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A {@link DaemonBuildRequest} described by a command line, to be parsed by the daemon (via the usual CLI
 * converter chain). This is the shape a non-JVM client can produce: it carries the arguments plus the working
 * directory, environment and system properties the daemon must apply, rather than a JVM {@code BuildAction}.
 *
 * <p>The daemon-side handling of this request is not implemented yet (Phase 4b); the current
 * {@link DaemonBuildExecuter} implementations reject it. The type exists now to pin the contract shape.
 */
@NullMarked
public final class ArgsBuildRequest implements DaemonBuildRequest {
    private final List<String> args;
    private final File workingDir;
    private final Map<String, String> environmentVariables;
    private final Map<String, String> systemProperties;
    private final BuildCancellationToken cancellationToken;
    private final BuildEventConsumer eventConsumer;

    public ArgsBuildRequest(
        List<String> args,
        File workingDir,
        Map<String, String> environmentVariables,
        Map<String, String> systemProperties,
        BuildCancellationToken cancellationToken,
        BuildEventConsumer eventConsumer
    ) {
        this.args = args;
        this.workingDir = workingDir;
        this.environmentVariables = environmentVariables;
        this.systemProperties = systemProperties;
        this.cancellationToken = cancellationToken;
        this.eventConsumer = eventConsumer;
    }

    public List<String> getArgs() {
        return args;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public BuildCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Override
    public BuildEventConsumer getEventConsumer() {
        return eventConsumer;
    }
}
