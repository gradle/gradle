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

import org.gradle.tooling.GradleConnection;
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion1;

import java.io.File;

/**
 * This is the main internal entry point for the tooling API.
 */
public class ConnectionFactory {
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ToolingImplementationLoader toolingImplementationLoader;

    public ConnectionFactory() {
        this(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
    }

    ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader) {
        this.toolingImplementationLoader = toolingImplementationLoader;
    }

    public GradleConnection create(Distribution distribution, File projectDir) {
        ConnectionFactoryVersion1 factory = toolingImplementationLoader.create(distribution);
        final ConnectionVersion1 connection = factory.create(projectDir);
        return new DefaultGradleConnection(connection, adapter);
    }

}
