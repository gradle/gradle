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

import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.PathToFileResolver;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

@NullMarked
public class DefaultClientExecHandleBuilderFactory implements ClientExecHandleBuilderFactory {

    private final PathToFileResolver fileResolver;
    private final Executor executor;
    private final BuildCancellationToken buildCancellationToken;

    private DefaultClientExecHandleBuilderFactory(
        PathToFileResolver fileResolver,
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

    public static DefaultClientExecHandleBuilderFactory of(
        PathToFileResolver fileResolver,
        ExecutorFactory executorFactory,
        BuildCancellationToken buildCancellationToken
    ) {
        ManagedExecutor executor = executorFactory.create("Exec process");
        return new DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken);
    }

    public static DefaultClientExecHandleBuilderFactory of(
        PathToFileResolver fileResolver,
        Executor executor,
        BuildCancellationToken buildCancellationToken
    ) {
        return new DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken);
    }

    /**
     * An instance of {@link ClientExecHandleBuilderFactory} that delegates to DefaultClientExecHandleBuilderFactory, but is also Stoppable.
     *
     * This is only used in DefaultDaemonStarter, and it should also stay this way. Ideally we would even remove it at one point.
     */
    @NullMarked
    public static class RootClientExecHandleBuilderFactory implements ClientExecHandleBuilderFactory, Stoppable {
        private final DefaultClientExecHandleBuilderFactory delegate;

        private RootClientExecHandleBuilderFactory(DefaultClientExecHandleBuilderFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public ClientExecHandleBuilder newExecHandleBuilder() {
            return delegate.newExecHandleBuilder();
        }

        @Override
        public void stop() {
            CompositeStoppable.stoppable(delegate.executor).stop();
        }

        /**
         * Creates a new {@link RootClientExecHandleBuilderFactory} for Daemon starter.
         *
         * This instance has unmanaged executor so the caller has to call {@link #stop()} to stop when instance is not needed anymore.
         */
        public static RootClientExecHandleBuilderFactory of(File gradleUserHome) {
            requireNonNull(gradleUserHome, "gradleUserHome");
            DefaultClientExecHandleBuilderFactory clientExecHandleBuilderFactory = DefaultClientExecHandleBuilderFactory.of(new DefaultFileLookup().getFileResolver(), new DefaultExecutorFactory(), new DefaultBuildCancellationToken());
            return new RootClientExecHandleBuilderFactory(clientExecHandleBuilderFactory);
        }
    }
}
