/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.loader;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class CachingToolingImplementationLoader implements ToolingImplementationLoader, Closeable {
    private final ToolingImplementationLoader loader;
    private final Cache<ClassPath, ConsumerConnection> connections = CacheBuilder.newBuilder()
        .maximumSize(15)
        .build();

    public CachingToolingImplementationLoader(ToolingImplementationLoader loader) {
        this.loader = loader;
    }

    @Override
    public ConsumerConnection create(final Distribution distribution, final ProgressLoggerFactory progressLoggerFactory, final InternalBuildProgressListener progressListener, final ConnectionParameters connectionParameters, final BuildCancellationToken cancellationToken) {
        ClassPath classpath = distribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken);

        try {
            return connections.get(classpath, new ConsumerConnectionCreator(distribution, progressLoggerFactory, progressListener, connectionParameters, cancellationToken));
        } catch (ExecutionException e) {
            throw new GradleConnectionException(String.format("Could not create an instance of Tooling API implementation using the specified %s.", distribution.getDisplayName()), e);
        }
    }

    @Override
    public void close() {
        try {
            CompositeStoppable.stoppable(connections.asMap().values()).stop();
        } finally {
            connections.invalidateAll();
        }
    }

    @NonNullApi
    private class ConsumerConnectionCreator implements Callable<ConsumerConnection> {
        private final Distribution distribution;
        private final ProgressLoggerFactory progressLoggerFactory;
        private final InternalBuildProgressListener progressListener;
        private final ConnectionParameters connectionParameters;
        private final BuildCancellationToken cancellationToken;

        public ConsumerConnectionCreator(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
            this.distribution = distribution;
            this.progressLoggerFactory = progressLoggerFactory;
            this.progressListener = progressListener;
            this.connectionParameters = connectionParameters;
            this.cancellationToken = cancellationToken;
        }

        @Override
        public ConsumerConnection call() throws Exception {
            return loader.create(distribution, progressLoggerFactory, progressListener, connectionParameters, cancellationToken);
        }

    }
}
