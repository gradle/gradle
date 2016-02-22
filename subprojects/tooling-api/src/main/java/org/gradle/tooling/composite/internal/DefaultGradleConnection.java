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
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.composite.GradleConnection;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultCompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.DistributionFactory;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnection implements GradleConnectionInternal {
    public static final class Builder implements GradleConnectionInternal.Builder {
        private final Set<GradleParticipantBuild> participants = Sets.newLinkedHashSet();
        private final GradleConnectionFactory gradleConnectionFactory;
        private final DistributionFactory distributionFactory;
        private File gradleUserHomeDir;
        private Boolean embeddedCoordinator;
        private Integer daemonMaxIdleTimeValue;
        private TimeUnit daemonMaxIdleTimeUnits;
        private File daemonBaseDir;
        private boolean useClasspathDistribution;
        private boolean emeddedParticipants;

        public Builder(GradleConnectionFactory gradleConnectionFactory, DistributionFactory distributionFactory) {
            this.gradleConnectionFactory = gradleConnectionFactory;
            this.distributionFactory = distributionFactory;
        }

        @Override
        public GradleConnection.Builder useGradleUserHomeDir(File gradleUserHomeDir) {
            this.gradleUserHomeDir = gradleUserHomeDir;
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, File gradleHome) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleHome));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, String gradleVersion) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleVersion));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, URI gradleDistribution) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleDistribution));
            return this;
        }

        @Override
        public GradleConnection build() throws GradleConnectionException {
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

            final Distribution distribution;
            if (useClasspathDistribution) {
                distribution = distributionFactory.getClasspathDistribution();
            } else {
                distribution = FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, participants);
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
            this.useClasspathDistribution = true;
            return this;
        }

        @Override
        public GradleConnectionInternal.Builder embeddedParticipants(boolean embedded) {
            this.emeddedParticipants = true;
            return this;
        }
    }

    private final AsyncConsumerActionExecutor asyncConnection;
    private final CompositeConnectionParameters parameters;

    DefaultGradleConnection(AsyncConsumerActionExecutor asyncConnection, CompositeConnectionParameters parameters) {
        this.asyncConnection = asyncConnection;
        this.parameters = parameters;
    }

    @Override
    public <T> Set<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return models(modelType).get();
    }

    @Override
    public <T> void getModels(Class<T> modelType, ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        models(modelType).get(handler);
    }

    @Override
    public <T> ModelBuilder<Set<T>> models(Class<T> modelType) {
        checkSupportedModelType(modelType);
        return new DefaultCompositeModelBuilder<T>(modelType, asyncConnection, parameters);
    }

    private <T> void checkSupportedModelType(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }

        // TODO: Remove
        if (!modelType.equals(EclipseProject.class)) {
            throw new UnsupportedOperationException(String.format("The only supported model for a Gradle composite is %s.class.", EclipseProject.class.getSimpleName()));
        }
    }

    @Override
    public void close() {
        asyncConnection.stop();
    }
}
