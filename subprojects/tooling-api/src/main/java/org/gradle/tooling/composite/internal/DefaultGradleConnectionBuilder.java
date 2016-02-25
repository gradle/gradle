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

package org.gradle.tooling.composite.internal;

import com.google.common.collect.Sets;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.composite.GradleBuild;
import org.gradle.tooling.composite.GradleConnection;
import org.gradle.tooling.internal.consumer.DefaultCompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnectionBuilder implements GradleConnectionInternal.Builder {
    private final Set<GradleBuildInternal> participants = Sets.newLinkedHashSet();
    private final GradleConnectionFactory gradleConnectionFactory;
    private final DistributionFactory distributionFactory;
    private File gradleUserHomeDir;
    private Boolean embeddedCoordinator;
    private Integer daemonMaxIdleTimeValue;
    private TimeUnit daemonMaxIdleTimeUnits;
    private File daemonBaseDir;
    private Distribution coordinatorDistribution;
    private boolean emeddedParticipants;

    public DefaultGradleConnectionBuilder(GradleConnectionFactory gradleConnectionFactory, DistributionFactory distributionFactory) {
        this.gradleConnectionFactory = gradleConnectionFactory;
        this.distributionFactory = distributionFactory;
    }

    @Override
    public GradleConnection.Builder useGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    @Override
    public GradleConnection.Builder addBuild(GradleBuild... gradleBuilds) {
        for (GradleBuild gradleBuild : gradleBuilds) {
            addBuild(gradleBuild);
        }
        return this;
    }

    private void addBuild(GradleBuild gradleBuild) {
        if (gradleBuild==null) {
            throw new NullPointerException("gradleBuild must not be null");
        }
        if (!(gradleBuild instanceof GradleBuildInternal)) {
            throw new IllegalArgumentException("GradleBuild has an internal API that must be implemented.");
        }
        participants.add((GradleBuildInternal)gradleBuild);
    }

    @Override
    public GradleConnectionInternal build() throws GradleConnectionException {
        if (participants.isEmpty()) {
            throw new IllegalStateException("At least one participant must be specified before creating a connection.");
        }

        DefaultCompositeConnectionParameters.Builder compositeConnectionParametersBuilder = DefaultCompositeConnectionParameters.builder();
        compositeConnectionParametersBuilder.setBuilds(participants);
        compositeConnectionParametersBuilder.setGradleUserHomeDir(gradleUserHomeDir);
        compositeConnectionParametersBuilder.setEmbedded(embeddedCoordinator);
        compositeConnectionParametersBuilder.setDaemonMaxIdleTimeValue(daemonMaxIdleTimeValue);
        compositeConnectionParametersBuilder.setDaemonMaxIdleTimeUnits(daemonMaxIdleTimeUnits);
        compositeConnectionParametersBuilder.setDaemonBaseDir(daemonBaseDir);
        compositeConnectionParametersBuilder.setEmbeddedParticipants(emeddedParticipants);

        DefaultCompositeConnectionParameters connectionParameters = compositeConnectionParametersBuilder.build();

        Distribution distribution = coordinatorDistribution;
        if (distribution == null) {
            distribution = distributionFactory.getDistribution(GradleVersion.current().getVersion());
        }
        return gradleConnectionFactory.create(distribution, connectionParameters);
    }

    @Override
    public GradleConnectionInternal.Builder embeddedCoordinator(boolean embedded) {
        this.embeddedCoordinator = embedded;
        return this;
    }

    @Override
    public GradleConnectionInternal.Builder daemonMaxIdleTime(int timeoutValue, TimeUnit timeoutUnits) {
        this.daemonMaxIdleTimeValue = timeoutValue;
        this.daemonMaxIdleTimeUnits = timeoutUnits;
        return this;
    }

    @Override
    public GradleConnectionInternal.Builder daemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }

    @Override
    public GradleConnectionInternal.Builder useClasspathDistribution() {
        this.coordinatorDistribution = distributionFactory.getClasspathDistribution();
        return this;
    }

    @Override
    public GradleConnectionInternal.Builder useInstallation(File gradleHome) {
        this.coordinatorDistribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    @Override
    public GradleConnectionInternal.Builder embeddedParticipants(boolean embedded) {
        this.emeddedParticipants = embedded;
        return this;
    }
}
