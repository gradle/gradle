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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;

public class CachingToolingImplementationLoader implements ToolingImplementationLoader, Closeable {
    private final ToolingImplementationLoader loader;
    private final Map<ClassPath, ConsumerConnection> connections = new HashMap<ClassPath, ConsumerConnection>();

    public CachingToolingImplementationLoader(ToolingImplementationLoader loader) {
        this.loader = loader;
    }

    public ConsumerConnection create(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
        ClassPath classpath = distribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters.getGradleUserHomeDir(), cancellationToken);

        ConsumerConnection connection = connections.get(classpath);
        if (connection == null) {
            connection = loader.create(distribution, progressLoggerFactory, progressListener, connectionParameters, cancellationToken);
            connections.put(classpath, connection);
        }

        return connection;
    }

    public void close() {
        try {
            CompositeStoppable.stoppable(connections.values()).stop();
        } finally {
            connections.clear();
        }
    }
}
