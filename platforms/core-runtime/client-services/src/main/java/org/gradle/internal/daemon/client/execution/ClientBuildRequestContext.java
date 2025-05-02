/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.daemon.client.execution;

import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;

/**
 * A client-side host for {@link org.gradle.internal.invocation.BuildAction} execution.
 */
public class ClientBuildRequestContext {
    private final GradleLauncherMetaData client;
    private final long startTime;
    private final boolean interactive;
    private final BuildCancellationToken cancellationToken;
    private final BuildEventConsumer eventConsumer;

    public ClientBuildRequestContext(
        GradleLauncherMetaData client,
        long startTime,
        boolean interactive,
        BuildCancellationToken cancellationToken,
        BuildEventConsumer eventConsumer
    ) {
        this.client = client;
        this.startTime = startTime;
        this.interactive = interactive;
        this.cancellationToken = cancellationToken;
        this.eventConsumer = eventConsumer;
    }

    public GradleLauncherMetaData getClient() {
        return client;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public BuildCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    public BuildEventConsumer getEventConsumer() {
        return eventConsumer;
    }
}
