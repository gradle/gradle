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

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnector extends GradleConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(GradleConnector.class);
    private final ConnectionFactory connectionFactory;
    private final DistributionFactory distributionFactory;
    private Distribution distribution;

    private final DefaultConnectionParameters.Builder connectionParamsBuilder = DefaultConnectionParameters.builder();

    public DefaultGradleConnector(ConnectionFactory connectionFactory, DistributionFactory distributionFactory) {
        this.connectionFactory = connectionFactory;
        this.distributionFactory = distributionFactory;
    }

    /**
     * Closes the tooling API, releasing all resources. Blocks until completed.
     *
     * <p>May attempt to expire some or all daemons started by this tooling API client. The exact behaviour here is implementation-specific and not guaranteed.
     * The expiration is best effort only. This method may return before the daemons have stopped.</p>
     *
     * <p>Note: this is not yet part of the public tooling API yet.</p>
     *
     * TODO - need to model this as a long running operation, and allow stdout, stderr and progress listener to be supplied.
     * TODO - need to define exceptions.
     * TODO - no further operations are allowed after this has been called
     * TODO - cancel current operations or block until complete
     * TODO - introduce a 'tooling API client' interface and move this method there
     */
    public static void close() {
        ConnectorServices.close();
    }

    @Override
    public GradleConnector useInstallation(File gradleHome) {
        distribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    @Override
    public GradleConnector useGradleVersion(String gradleVersion) {
        distribution = distributionFactory.getDistribution(gradleVersion);
        return this;
    }

    @Override
    public GradleConnector useDistribution(URI gradleDistribution) {
        distribution = distributionFactory.getDistribution(gradleDistribution);
        return this;
    }

    public GradleConnector useClasspathDistribution() {
        distribution = distributionFactory.getClasspathDistribution();
        return this;
    }

    @Override
    public GradleConnector useBuildDistribution() {
        distribution = null;
        return this;
    }

    public GradleConnector useDistributionBaseDir(File distributionBaseDir) {
        distributionFactory.setDistributionBaseDir(distributionBaseDir);
        return this;
    }

    @Override
    public GradleConnector forProjectDirectory(File projectDir) {
        connectionParamsBuilder.setProjectDir(projectDir);
        return this;
    }

    @Override
    public GradleConnector useGradleUserHomeDir(File gradleUserHomeDir) {
        connectionParamsBuilder.setGradleUserHomeDir(gradleUserHomeDir);
        return this;
    }

    public GradleConnector searchUpwards(boolean searchUpwards) {
        connectionParamsBuilder.setSearchUpwards(searchUpwards);
        return this;
    }

    public GradleConnector embedded(boolean embedded) {
        connectionParamsBuilder.setEmbedded(embedded);
        return this;
    }

    public GradleConnector daemonMaxIdleTime(int timeoutValue, TimeUnit timeoutUnits) {
        connectionParamsBuilder.setDaemonMaxIdleTimeValue(timeoutValue);
        connectionParamsBuilder.setDaemonMaxIdleTimeUnits(timeoutUnits);
        return this;
    }

    public GradleConnector daemonBaseDir(File daemonBaseDir) {
        connectionParamsBuilder.setDaemonBaseDir(daemonBaseDir);
        return this;
    }

    /**
     * If true then debug log statements will be shown
     */
    public DefaultGradleConnector setVerboseLogging(boolean verboseLogging) {
        connectionParamsBuilder.setVerboseLogging(verboseLogging);
        return this;
    }

    @Override
    public ProjectConnection connect() throws GradleConnectionException {
        LOGGER.debug("Connecting from tooling API consumer version {}", GradleVersion.current().getVersion());

        ConnectionParameters connectionParameters = connectionParamsBuilder.build();
        if (connectionParameters.getProjectDir() == null) {
            throw new IllegalStateException("A project directory must be specified before creating a connection.");
        }
        if (distribution == null) {
            distribution = distributionFactory.getDefaultDistribution(connectionParameters.getProjectDir(), connectionParameters.isSearchUpwards() != null ? connectionParameters.isSearchUpwards() : true);
        }
        return connectionFactory.create(distribution, connectionParameters);
    }

    ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }
}
