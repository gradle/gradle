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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildExecuter;

/**
 * Ensures that the client provided build start time is not after when the build request was received.
 *
 * The client started time cannot be entirely trusted as we cannot guarantee that its clock is in sync with the build runtime's.
 * By forcing it to be no later than when the runtime received the request, we avoid odd bugs such as negative build times.
 */
class NormalizeBuildStartTimeBuildExecuter implements BuildExecuter {

    private final Clock clock;
    private final BuildExecuter delegate;

    public NormalizeBuildStartTimeBuildExecuter(Clock clock, BuildExecuter delegate) {
        this.clock = clock;
        this.delegate = delegate;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        long currentTime = clock.getCurrentTime();
        if (requestContext.getStartTime() > currentTime) {
            BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(requestContext.getClient(), currentTime);
            requestContext = new DefaultBuildRequestContext(buildRequestMetaData, requestContext.getCancellationToken(), requestContext.getEventConsumer());
        }

        return delegate.execute(action, requestContext, actionParameters, contextServices);
    }

}
