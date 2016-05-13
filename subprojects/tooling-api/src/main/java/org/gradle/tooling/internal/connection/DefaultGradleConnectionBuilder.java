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
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnectionBuilder implements GradleConnectionBuilderInternal {
    private static final String WARNING_MESSAGE =
        "   - All participant builds will be executed in a single daemon process.\n"
        + "   - Java home settings for participants will be ignored.\n"
        + "   - Immutable JVM arguments (e.g. memory settings) will be ignored.\n";

    private final Set<DefaultGradleConnectionParticipantBuilder> participantBuilders = Sets.newLinkedHashSet();
    private final GradleConnectionFactory gradleConnectionFactory;
    private final DistributionFactory distributionFactory;
    private File gradleUserHomeDir;
    private Integer daemonMaxIdleTimeValue;
    private TimeUnit daemonMaxIdleTimeUnits;
    private File daemonBaseDir;

    private boolean integrated;
    private boolean embedded;
    private Distribution coordinatorDistribution;

    public DefaultGradleConnectionBuilder(GradleConnectionFactory gradleConnectionFactory, DistributionFactory distributionFactory) {
        this.gradleConnectionFactory = gradleConnectionFactory;
        this.distributionFactory = distributionFactory;
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

        if (integrated) {
            compositeConnectionParametersBuilder.setEmbedded(embedded);
            DefaultCompositeConnectionParameters connectionParameters = compositeConnectionParametersBuilder.build();

            DeprecationLogger.incubatingFeatureUsed("Integrated composite build", WARNING_MESSAGE);
            Distribution distribution = coordinatorDistribution;
            if (distribution == null) {
                distribution = distributionFactory.getDistribution(GradleVersion.current().getVersion());
            }
            return gradleConnectionFactory.create(distribution, connectionParameters, true);
        } else {
            DefaultCompositeConnectionParameters connectionParameters = compositeConnectionParametersBuilder.build();

            // The distribution is effectively ignored
            return gradleConnectionFactory.create(distributionFactory.getClasspathDistribution(), connectionParameters, false);
        }
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
    public GradleConnectionBuilderInternal integratedComposite(boolean integrated) {
        this.integrated = integrated;
        return this;
    }

    @Override
    public GradleConnectionBuilderInternal embedded(boolean embedded) {
        this.embedded = embedded;
        return this;
    }

    @Override
    public GradleConnectionBuilder useInstallation(File gradleHome) {
        this.coordinatorDistribution = distributionFactory.getDistribution(gradleHome);
        return this;
    }

    @Override
    public GradleConnectionBuilder useGradleVersion(String gradleVersion) {
        this.coordinatorDistribution = distributionFactory.getDistribution(gradleVersion);
        return this;
    }

    @Override
    public GradleConnectionBuilderInternal useClasspathDistribution() {
        this.coordinatorDistribution = distributionFactory.getClasspathDistribution();
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
