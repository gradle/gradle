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

import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionParameters;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link DaemonBuildRequest} that carries an in-JVM {@link BuildAction}. This is what the CLI and the Tooling
 * API produce today: a fully-resolved action + parameters. Only a JVM client can construct one; a non-JVM
 * client uses {@link ArgsBuildRequest} instead.
 */
@NullMarked
public final class JvmBuildRequest implements DaemonBuildRequest {
    private final BuildAction action;
    private final GradleLauncherMetaData client;
    private final long startTime;
    private final boolean interactiveConsole;
    private final BuildActionParameters parameters;
    private final BuildCancellationToken cancellationToken;
    private final BuildEventConsumer eventConsumer;

    public JvmBuildRequest(
        BuildAction action,
        GradleLauncherMetaData client,
        long startTime,
        boolean interactiveConsole,
        BuildActionParameters parameters,
        BuildCancellationToken cancellationToken,
        BuildEventConsumer eventConsumer
    ) {
        this.action = action;
        this.client = client;
        this.startTime = startTime;
        this.interactiveConsole = interactiveConsole;
        this.parameters = parameters;
        this.cancellationToken = cancellationToken;
        this.eventConsumer = eventConsumer;
    }

    public BuildAction getAction() {
        return action;
    }

    public GradleLauncherMetaData getClient() {
        return client;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isInteractiveConsole() {
        return interactiveConsole;
    }

    public BuildActionParameters getParameters() {
        return parameters;
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
