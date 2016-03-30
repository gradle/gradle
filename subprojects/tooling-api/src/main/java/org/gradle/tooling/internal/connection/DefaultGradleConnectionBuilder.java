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

import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.internal.composite.DefaultGradleParticipantBuild;
import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.connection.GradleConnection;
import org.gradle.tooling.connection.GradleConnectionBuilder;
import org.gradle.tooling.internal.consumer.DefaultCompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnectionBuilder implements GradleConnectionBuilderInternal {
    private final Set<DefaultGradleConnectionParticipantBuilder> participantBuilders = Sets.newLinkedHashSet();
    private final GradleConnectionFactory gradleConnectionFactory;
    private final DistributionFactory distributionFactory;
    private File gradleUserHomeDir;
    private Integer daemonMaxIdleTimeValue;
    private TimeUnit daemonMaxIdleTimeUnits;
    private File daemonBaseDir;

    public DefaultGradleConnectionBuilder(GradleConnectionFactory gradleConnectionFactory, DistributionFactory distributionFactory) {
        this.gradleConnectionFactory = gradleConnectionFactory;
        this.distributionFactory = distributionFactory;
    }

    @Override
    public GradleConnectionBuilder useGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    @Override
    public ParticipantBuilder addParticipant(File projectDirectory) {
        DefaultGradleConnectionParticipantBuilder participantBuilder = new DefaultGradleConnectionParticipantBuilder(projectDirectory);
        participantBuilders.add(participantBuilder);
        return participantBuilder;
    }

    @Override
    public GradleConnection build() throws GradleConnectionException {
        if (participantBuilders.isEmpty()) {
            throw new IllegalStateException("At least one participant must be specified before creating a connection.");
        }
        return createGradleConnection();
    }

    private GradleConnection createGradleConnection() {
        Set<GradleParticipantBuild> participants = CollectionUtils.collect(participantBuilders, new Transformer<GradleParticipantBuild, DefaultGradleConnectionParticipantBuilder>() {
            @Override
            public GradleParticipantBuild transform(DefaultGradleConnectionParticipantBuilder participantBuilder) {
                return participantBuilder.build();
            }
        });
        DefaultCompositeConnectionParameters.Builder compositeConnectionParametersBuilder = DefaultCompositeConnectionParameters.builder();
        compositeConnectionParametersBuilder.setBuilds(participants);
        compositeConnectionParametersBuilder.setGradleUserHomeDir(gradleUserHomeDir);
        compositeConnectionParametersBuilder.setDaemonMaxIdleTimeValue(daemonMaxIdleTimeValue);
        compositeConnectionParametersBuilder.setDaemonMaxIdleTimeUnits(daemonMaxIdleTimeUnits);
        compositeConnectionParametersBuilder.setDaemonBaseDir(daemonBaseDir);

        DefaultCompositeConnectionParameters connectionParameters = compositeConnectionParametersBuilder.build();

        Distribution distribution = distributionFactory.getDistribution(GradleVersion.current().getVersion());
        return gradleConnectionFactory.create(distribution, connectionParameters, false);
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

    private class DefaultGradleConnectionParticipantBuilder implements ParticipantBuilder {
        private final File projectDir;
        private File gradleHome;
        private URI gradleDistribution;
        private String gradleVersion;

        public DefaultGradleConnectionParticipantBuilder(File projectDir) {
            if (projectDir == null) {
                throw new IllegalArgumentException("Project directory cannot be null.");
            }
            this.projectDir = projectDir;
        }

        @Override
        public ParticipantBuilder useBuildDistribution() {
            resetDistribution();
            return this;
        }

        @Override
        public ParticipantBuilder useInstallation(File gradleHome) {
            resetDistribution();
            this.gradleHome = gradleHome;
            return this;
        }

        @Override
        public ParticipantBuilder useGradleVersion(String gradleVersion) {
            resetDistribution();
            this.gradleVersion = gradleVersion;
            return this;
        }

        @Override
        public ParticipantBuilder useDistribution(URI gradleDistribution) {
            resetDistribution();
            this.gradleDistribution = gradleDistribution;
            return this;
        }

        public GradleParticipantBuild build() {
            return new DefaultGradleParticipantBuild(projectDir, gradleHome, gradleDistribution, gradleVersion);
        }

        private void resetDistribution() {
            this.gradleHome = null;
            this.gradleDistribution = null;
            this.gradleVersion = null;
        }
    }

}
