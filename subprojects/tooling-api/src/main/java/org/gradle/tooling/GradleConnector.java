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

import org.gradle.tooling.internal.consumer.ConnectionFactory;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;

import java.io.File;

/**
 * <p>A {@code GradleConnector} is the main entry point to the Gradle tooling API. You use this API as follows:</p>
 *
 * <ol>
 *
 * <li>Call {@link #newConnector()} to create a new connector instance.</li>
 *
 * <li>Configure the connector.</li>
 *
 * <li>Call {@link #connect()} to create the connection.</li>
 *
 * <li>Optionally reuse the connector to create additional connections.</li>
 *
 * <li>When finished with the connections, call {@link #close()} to clean up.</li>
 *
 * </ol>
 *
 * <p>{@code GradleConnector} instances are not thread-safe.</p>
 */
public class GradleConnector {
    private final ConnectionFactory connectionFactory;
    private final DistributionFactory distributionFactory;
    private File projectDir;
    private Distribution distribution;

    GradleConnector(ConnectionFactory connectionFactory, DistributionFactory distributionFactory) {
        this.connectionFactory = connectionFactory;
        this.distributionFactory = distributionFactory;
    }

    /**
     * Creates a new connector instance.
     *
     * @return The instance. Never returns null.
     */
    public static GradleConnector newConnector() {
        return new GradleConnector(new ConnectionFactory(), new DistributionFactory());
    }

    /**
     * Specifies which Gradle installation to use.
     *
     * @param gradleHome The Gradle installation directory.
     * @return this
     */
    public GradleConnector useInstallation(File gradleHome) {
        distribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    /**
     * Specifies which Gradle version to use. The appropriate distribution is downloaded into the user's Gradle home directory.
     *
     * @param gradleVersion The version to use.
     * @return this
     */
    public GradleConnector useGradleVersion(String gradleVersion) {
        throw new UnsupportedOperationException();
    }

    /**
     * Specifies the working directory to use.
     *
     * @param projectDir The working directory.
     * @return this
     */
    public GradleConnector forProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    /**
     * Creates the connection.
     *
     * @return The connection. Never return null.
     * @throws UnsupportedVersionException When the target Gradle version does not support this version of the tooling API.
     * @throws GradleConnectionException On failure to establish a connection with the target Gradle version.
     */
    public GradleConnection connect() throws GradleConnectionException {
        if (projectDir == null) {
            throw new IllegalStateException("A project directory must be specified before creating a connection.");
        }
        if (distribution == null) {
            distribution = distributionFactory.getCurrentDistribution();
        }
        return connectionFactory.create(distribution, projectDir);
    }

    /**
     * Closes this connector and all connections created by it.
     */
    public void close() {
    }
}
