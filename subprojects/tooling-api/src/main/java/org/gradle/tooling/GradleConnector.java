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

import org.gradle.tooling.internal.consumer.GradleConnectionFactory;

import java.io.File;

/**
 * A {@code GradleConnector} is the main entry point to the Gradle tooling API.
 */
public class GradleConnector {
    private final GradleConnectionFactory connectionFactory;
    private File projectDir;

    GradleConnector(GradleConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public static GradleConnector newConnector() {
        return new GradleConnector(new GradleConnectionFactory());
    }

    public GradleConnector useInstallation(File gradleHome) {
        return this;
    }

    public GradleConnector useGradleVersion(String gradleVersion) {
        return this;
    }

    public GradleConnector forProjectDirectory(File projectDir)  {
        this.projectDir = projectDir;
        return this;
    }

    /**
     * Creates the connection.
     * @return The connection.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support this version of the tooling API.
     * @throws GradleConnectionException On failure to establish a connection with the target Gradle version.
     */
    public GradleConnection connect() throws GradleConnectionException {
        if (projectDir == null) {
            throw new IllegalStateException("A project directory must be specified before creating a connection.");
        }
        return connectionFactory.create(projectDir);
    }
    
    public void close() {
    }
}
