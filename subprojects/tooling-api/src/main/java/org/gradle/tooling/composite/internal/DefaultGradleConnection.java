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
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.net.URI;
import java.util.Set;

public class DefaultGradleConnection implements GradleConnection {
    public static final class Builder implements GradleConnection.Builder {

        private File gradleUserHomeDir;
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
            }

            return new DefaultGradleConnection(gradleUserHomeDir, participants);
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
        checkSupportedModelType(modelType);
        checkValidComposite();
        return createCompositeModelBuilder(modelType);
    }

    private <T> CompositeModelBuilder<T> createCompositeModelBuilder(Class<T> modelType) {
        return new DefaultCompositeModelBuilder<T>(modelType, participants);
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

    private void checkValidComposite() {
        // TODO: Should we bother doing this validation all client-side?
        /*
        Set<BuildEnvironment> buildEnvironments = createCompositeModelBuilder(BuildEnvironment.class).get();

        final VersionNumber minimumVersion = VersionNumber.parse("1.0");
        CollectionUtils.every(buildEnvironments, new Spec<BuildEnvironment>() {
            @Override
            public boolean isSatisfiedBy(BuildEnvironment buildEnvironment) {
                // TODO: Need project name information
                String projectName = "unknown project";
                VersionNumber participantVersion = VersionNumber.parse(buildEnvironment.getGradle().getGradleVersion());
                if (participantVersion.compareTo(minimumVersion) >= 0) {
                    return true;
                }
                throw new IllegalArgumentException(String.format("'%s' is using Gradle %s.  This does not meet the minimum Gradle version (%s) required for composite builds.", projectName, participantVersion, minimumVersion));
            }
        });
        */

        // TODO: Need to skip checking root projects and their subprojects
        /*
        Set<GradleProject> gradleProjects = createCompositeModelBuilder(GradleProject.class).get();
        List<String> projectDirectories = CollectionUtils.collect((Iterable<GradleProject>)gradleProjects, new Transformer<String, GradleProject>() {
            @Override
            public String transform(GradleProject gradleProject) {
                return gradleProject.getProjectDirectory().getAbsolutePath();
            }
        });

        for (int i=0; i<projectDirectories.size(); i++) {
            String first = projectDirectories.get(i);
            for (int j=i; j<projectDirectories.size(); j++) {
                String second = projectDirectories.get(j);
                try {
                    if (FilenameUtils.directoryContains(first, second) ||
                        FilenameUtils.directoryContains(second, first)) {
                        throw new IllegalArgumentException(String.format("%s and %s have overlapping project directories. Composite builds must not overlap.", first, second));
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }*/
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(participants).stop();
    }
}
