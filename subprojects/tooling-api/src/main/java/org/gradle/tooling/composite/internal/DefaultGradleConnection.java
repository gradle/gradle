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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.composite.CompositeModelBuilder;
import org.gradle.tooling.composite.GradleConnection;

import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DefaultGradleConnection implements GradleConnection {
    public static final class Builder implements GradleConnectionInternal.Builder {
        private File gradleUserHomeDir;
        private File daemonBaseDir;
        private final Set<GradleParticipantBuild> participants = Sets.newHashSet();

        @Override
        public GradleConnection.Builder useGradleUserHomeDir(File gradleUserHomeDir) {
            this.gradleUserHomeDir = gradleUserHomeDir;
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleUserHomeDir));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, File gradleHome) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleUserHomeDir, gradleHome));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, String gradleVersion) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleUserHomeDir, gradleVersion));
            return this;
        }

        @Override
        public GradleConnection.Builder addBuild(File rootProjectDirectory, URI gradleDistribution) {
            participants.add(new DefaultGradleParticipantBuild(rootProjectDirectory, gradleUserHomeDir, gradleDistribution));
            return this;
        }

        @Override
        public GradleConnection build() throws GradleConnectionException {
            if (participants.isEmpty()) {
                throw new IllegalStateException("At least one participant must be specified before creating a connection.");
            }

            // Set Gradle user home for each participant build
            for (GradleParticipantBuild participant : participants) {
                participant.setGradleUserHomeDir(gradleUserHomeDir);
                participant.setDaemonBaseDir(daemonBaseDir);
            }

            return new ValidatingGradleConnection(new DefaultGradleConnection(gradleUserHomeDir, participants), new DefaultCompositeValidator());
        }

        @Override
        public GradleConnectionInternal.Builder embeddedCoordinator(boolean embedded) {
            // ignored
            return this;
        }

        @Override
        public GradleConnectionInternal.Builder daemonMaxIdleTime(int timeoutValue, TimeUnit timeoutUnits) {
            // ignore
            return this;
        }

        @Override
        public GradleConnectionInternal.Builder daemonBaseDir(File daemonBaseDir) {
            this.daemonBaseDir = daemonBaseDir;
            return this;
        }
    }

    private File gradleUserHomeDir;
    private final Set<GradleParticipantBuild> participants;

    private DefaultGradleConnection(File gradleUserHomeDir, Set<GradleParticipantBuild> participants) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.participants = participants;
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
    public <T> CompositeModelBuilder<T> models(Class<T> modelType) {
        return createCompositeModelBuilder(modelType);
    }

    private <T> CompositeModelBuilder<T> createCompositeModelBuilder(Class<T> modelType) {
        return new DefaultCompositeModelBuilder<T>(modelType, participants);
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(participants).stop();
    }
}
