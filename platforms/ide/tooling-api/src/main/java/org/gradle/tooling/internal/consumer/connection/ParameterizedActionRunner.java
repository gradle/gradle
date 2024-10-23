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

import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection;

class ParameterizedActionRunner extends CancellableActionRunner {
    private final InternalParameterAcceptingConnection executor;

    ParameterizedActionRunner(InternalParameterAcceptingConnection executor, CancellationExceptionTransformer exceptionTransformer, VersionDetails versionDetails) {
        super(null, exceptionTransformer, versionDetails);
        this.executor = executor;
    }

    @Override
    protected <T> BuildResult<T> execute(InternalBuildActionAdapter<T> buildActionAdapter, InternalCancellationToken cancellationTokenAdapter, BuildParameters operationParameters) {
        return executor.run(buildActionAdapter, cancellationTokenAdapter, operationParameters);
    }
}
