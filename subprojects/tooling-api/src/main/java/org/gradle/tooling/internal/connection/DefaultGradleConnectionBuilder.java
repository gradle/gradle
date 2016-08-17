/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.connection.GradleConnection;
import org.gradle.tooling.connection.GradleConnectionBuilder;
import org.gradle.tooling.internal.consumer.DefaultConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnectionBuilder implements GradleConnectionBuilderInternal {

    private final GradleConnectionFactory gradleConnectionFactory;
    private final DistributionFactory distributionFactory;
    private File rootDirectory;
    private Distribution distribution;
    private File gradleUserHomeDir;
    private Integer daemonMaxIdleTimeValue;
    private TimeUnit daemonMaxIdleTimeUnits;
    private File daemonBaseDir;
    private boolean verboseLogging;
    private boolean embedded;

    public DefaultGradleConnectionBuilder(GradleConnectionFactory gradleConnectionFactory, DistributionFactory distributionFactory) {
        this.gradleConnectionFactory = gradleConnectionFactory;
        this.distributionFactory = distributionFactory;
    }

    @Override
    public GradleConnectionBuilder forRootDirectory(File rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public GradleConnectionBuilderInternal useInstallation(File gradleHome) {
        distribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    public GradleConnectionBuilderInternal useGradleVersion(String gradleVersion) {
        distribution = distributionFactory.getDistribution(gradleVersion);
        return this;
    }

    public GradleConnectionBuilderInternal useDistribution(URI gradleDistribution) {
        distribution = distributionFactory.getDistribution(gradleDistribution);
        return this;
    }

    public GradleConnectionBuilderInternal useClasspathDistribution() {
        distribution = distributionFactory.getClasspathDistribution();
        return this;
    }

    public GradleConnectionBuilderInternal useBuildDistribution() {
        distribution = null;
        return this;
    }

    @Override
    public GradleConnectionBuilder useGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    @Override
    public GradleConnectionBuilderInternal daemonMaxIdleTime(int timeoutValue, TimeUnit timeoutUnits) {
        this.daemonMaxIdleTimeValue = timeoutValue;
        this.daemonMaxIdleTimeUnits = timeoutUnits;
        return this;
    }

    @Override
    public GradleConnectionBuilderInternal daemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }
    @Override
    public GradleConnectionBuilderInternal embedded(boolean embedded) {
        this.embedded = embedded;
        return this;
    }

    @Override
    public DefaultGradleConnectionBuilder setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
        return this;
    }

    @Override
    public GradleConnection build() throws GradleConnectionException {
        return createGradleConnection();
    }

    private GradleConnection createGradleConnection() {

        DefaultConnectionParameters connectionParameters = DefaultConnectionParameters.builder()
            .setGradleUserHomeDir(gradleUserHomeDir)
            .setDaemonMaxIdleTimeValue(daemonMaxIdleTimeValue)
            .setDaemonMaxIdleTimeUnits(daemonMaxIdleTimeUnits)
            .setDaemonBaseDir(daemonBaseDir)
            .setEmbedded(embedded)
            .setProjectDir(rootDirectory)
            .setSearchUpwards(false)
            .setVerboseLogging(verboseLogging)
            .build();

        if (distribution == null) {
            distribution = distributionFactory.getDistribution(GradleVersion.current().getVersion());
        }
        return gradleConnectionFactory.create(distribution, connectionParameters);
    }
}
