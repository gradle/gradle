/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.base.Throwables;
import org.gradle.api.internal.configuration.BuildRestartException;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;

public class RetryBuildActionExecutor implements BuildSessionActionExecutor {
    private static final int RETRY_LIMIT = 1;
    private final BuildSessionActionExecutor delegate;

    public RetryBuildActionExecutor(BuildSessionActionExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext context) {
        int retries = 0;
        while (retries <= RETRY_LIMIT) {
            BuildActionRunner.Result result = delegate.execute(action, context);
            if (result.hasResult() && result.getBuildFailure() != null) {
                Throwable rootCause = Throwables.getRootCause(result.getBuildFailure());
                if (rootCause instanceof BuildRestartException) {
                    System.out.printf("Restart requested\n");
                    BuildRestartException buildFailure = (BuildRestartException) rootCause;
                    StartParameterInternal startParameters = context.getServices().get(StartParameterInternal.class);
                    buildFailure.customize(startParameters);
                    retries++;
                } else {
                    return result;
                }
            } else {
                return result;
            }
        }
        throw new IllegalStateException("Too many retries requested");
    }
}
