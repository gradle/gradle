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

package org.gradle.process.internal.services;

import org.gradle.api.internal.file.FileResolver;
import org.jspecify.annotations.NullMarked;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.process.internal.DefaultClientExecHandleBuilderFactory;
import org.gradle.process.internal.ExecHandleTrackingExecutor;

@NullMarked
public class ProcessBasicGlobalScopeServices implements ServiceRegistrationProvider {
    /**
     * Provided as its own {@link org.gradle.internal.concurrent.Stoppable} service so the registry destroys
     * any in-flight processes on teardown instead of leaving their output readers blocked on a live pipe.
     */
    @Provides
    ExecHandleTrackingExecutor createExecProcessExecutor(ExecutorFactory executorFactory) {
        return ExecHandleTrackingExecutor.create(executorFactory);
    }

    @Provides
    ClientExecHandleBuilderFactory createExecHandleFactory(
        FileResolver fileResolver,
        ExecHandleTrackingExecutor execProcessExecutor,
        BuildCancellationToken buildCancellationToken
    ) {
        return DefaultClientExecHandleBuilderFactory.of(fileResolver, execProcessExecutor, buildCancellationToken);
    }
}
