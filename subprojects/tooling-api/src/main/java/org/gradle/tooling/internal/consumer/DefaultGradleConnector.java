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

import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;

public class DefaultGradleConnector extends GradleConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(GradleConnector.class);
    private final ConnectionFactory connectionFactory;
    private final DistributionFactory distributionFactory;
    private File projectDir;
    private File gradleUserHomeDir;
    private Distribution distribution;
    private Boolean searchUpwards;

    public DefaultGradleConnector(ConnectionFactory connectionFactory, DistributionFactory distributionFactory) {
        this.connectionFactory = connectionFactory;
        this.distributionFactory = distributionFactory;
    }

    public GradleConnector useInstallation(File gradleHome) {
        distribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    public GradleConnector useGradleVersion(String gradleVersion) {
        distribution = distributionFactory.getDistribution(gradleVersion);
        return this;
    }

    public GradleConnector useDistribution(URI gradleDistribution) {
        distribution = distributionFactory.getDistribution(gradleDistribution);
        return this;
    }

    public GradleConnector forProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public GradleConnector useGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    public GradleConnector searchUpwards(boolean searchUpwards) {
        this.searchUpwards = searchUpwards;
        return this;
    }

    public ProjectConnection connect() throws GradleConnectionException {
        LOGGER.debug("Connecting from tooling API consumer version {}", GradleVersion.current().getVersion());

        if (projectDir == null) {
            throw new IllegalStateException("A project directory must be specified before creating a connection.");
        }
        if (distribution == null) {
            distribution = distributionFactory.getCurrentDistribution();
        }
        return connectionFactory.create(distribution, new DefaultConnectionParametersVersion1(projectDir, gradleUserHomeDir, searchUpwards));
    }

}
