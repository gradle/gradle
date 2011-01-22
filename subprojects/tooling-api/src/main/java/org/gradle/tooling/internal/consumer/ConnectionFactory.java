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
package org.gradle.tooling.internal.consumer;

import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.tooling.BuildConnection;
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion1;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * This is the main internal entry point for the tooling API.
 *
 * This implementation is thread-safe.
 */
public class ConnectionFactory implements Stoppable {
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ToolingImplementationLoader toolingImplementationLoader;
    private final Set<ConnectionFactoryVersion1> factories = new CopyOnWriteArraySet<ConnectionFactoryVersion1>();

    public ConnectionFactory() {
        this(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
    }

    ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader) {
        this.toolingImplementationLoader = toolingImplementationLoader;
    }

    public BuildConnection create(Distribution distribution, File projectDir) {
        ConnectionFactoryVersion1 factory = toolingImplementationLoader.create(distribution);
        factories.add(factory);
        final ConnectionVersion1 connection = factory.create(projectDir);
        return new DefaultBuildConnection(connection, adapter);
    }

    public void stop() {
        for (ConnectionFactoryVersion1 factory : factories) {
            factory.stop();
        }
    }
}
