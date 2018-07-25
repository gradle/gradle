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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Transformer;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;

import java.io.File;

class CancellableActionRunner implements ActionRunner {
    private final InternalCancellableConnection executor;
    private final Transformer<RuntimeException, RuntimeException> exceptionTransformer;
    private final VersionDetails versionDetails;

    CancellableActionRunner(InternalCancellableConnection executor, Transformer<RuntimeException, RuntimeException> exceptionTransformer, VersionDetails versionDetails) {
        this.executor = executor;
        this.exceptionTransformer = exceptionTransformer;
        this.versionDetails = versionDetails;
    }

    public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters)
        throws UnsupportedOperationException, IllegalStateException {

        File rootDir = operationParameters.getProjectDir();
        BuildResult<T> result;
        try {
            try {
                result = execute(new InternalBuildActionAdapter<T>(action, rootDir, versionDetails), new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
            } catch (RuntimeException e) {
                throw exceptionTransformer.transform(e);
            }
        } catch (InternalBuildActionFailureException e) {
            throw new BuildActionFailureException("The supplied build action failed with an exception.", e.getCause());
        }
        return result.getModel();
    }

    protected <T> BuildResult<T> execute(InternalBuildActionAdapter<T> buildActionAdapter, InternalCancellationToken cancellationTokenAdapter, BuildParameters operationParameters) {
        return executor.run(buildActionAdapter, cancellationTokenAdapter, operationParameters);
    }
}
