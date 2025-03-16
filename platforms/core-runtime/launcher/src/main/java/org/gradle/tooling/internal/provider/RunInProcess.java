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

package org.gradle.tooling.internal.provider;

import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

/**
 * Maps from the client view of a request to the daemon view. Used when a build is run in the client.
 */
public class RunInProcess implements BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext> {
    private final BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate;

    public RunInProcess(BuildActionExecutor<BuildActionParameters, BuildRequestContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, ClientBuildRequestContext context) {
        DefaultBuildRequestMetaData requestMetadata = new DefaultBuildRequestMetaData(new DefaultBuildClientMetaData(context.getClient()), context.getStartTime(), context.isInteractive());
        return delegate.execute(action, actionParameters, new DefaultBuildRequestContext(requestMetadata, context.getCancellationToken(), context.getEventConsumer()));
    }
}
