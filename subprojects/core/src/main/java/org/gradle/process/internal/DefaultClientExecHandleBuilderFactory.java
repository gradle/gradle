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

package org.gradle.process.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;

import java.io.File;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

@NonNullApi
public class DefaultClientExecHandleBuilderFactory implements ClientExecHandleBuilderFactory, Stoppable {

    private final FileResolver fileResolver;
    private final Executor executor;
    private final BuildCancellationToken buildCancellationToken;

    private DefaultClientExecHandleBuilderFactory(
        FileResolver fileResolver,
        Executor executor,
        BuildCancellationToken buildCancellationToken
    ) {
        this.fileResolver = fileResolver;
        this.executor = executor;
        this.buildCancellationToken = buildCancellationToken;
    }

    @Override
    public ClientExecHandleBuilder newExecHandleBuilder() {
        return new DefaultClientExecHandleBuilder(fileResolver, executor, buildCancellationToken);
    }

    public static DefaultClientExecHandleBuilderFactory root(File gradleUserHome) {
        requireNonNull(gradleUserHome, "gradleUserHome");
        return of(new DefaultFileLookup().getFileResolver(), new DefaultExecutorFactory(), new DefaultBuildCancellationToken());
    }

    public static DefaultClientExecHandleBuilderFactory of(
        FileResolver fileResolver,
        ExecutorFactory executorFactory,
        BuildCancellationToken buildCancellationToken
    ) {
        ManagedExecutor executor = executorFactory.create("Exec process");
        return new DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(executor).stop();
    }
}
