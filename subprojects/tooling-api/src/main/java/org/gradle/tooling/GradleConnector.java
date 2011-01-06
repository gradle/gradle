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
package org.gradle.tooling;

import org.gradle.tooling.internal.DefaultGradleConnectionFactory;
import org.gradle.tooling.internal.ProtocolToModelAdapter;
import org.gradle.tooling.internal.protocol.GradleConnectionFactoryVersion1;
import org.gradle.tooling.internal.protocol.GradleConnectionVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseBuildVersion1;
import org.gradle.tooling.model.Build;

import java.io.File;

/**
 * A {@code GradleConnector} is the main entry point to the Gradle tooling API.
 */
public class GradleConnector {
    public static GradleConnector newConnector() {
        return new GradleConnector();
    }

    public GradleConnector useInstallation(File gradleHome) {
        return this;
    }

    public GradleConnector useGradleVersion(String gradleVersion) {
        return this;
    }

    public GradleConnection forProjectDirectory(File projectDir) throws UnsupportedVersionException {
        final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
        GradleConnectionFactoryVersion1 factory = new DefaultGradleConnectionFactory();
        final GradleConnectionVersion1 gradleConnection = factory.create(projectDir);
        return new GradleConnection() {
            public <T extends Build> T getModel(Class<T> viewType) throws UnsupportedVersionException {
                return adapter.adapt(viewType, gradleConnection.getModel(EclipseBuildVersion1.class));
            }
        };
    }

    public void close() {
    }
}
