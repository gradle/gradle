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

package org.gradle.tooling.internal.composite;

import org.gradle.api.Transformer;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.BuildIdentity;
import org.gradle.tooling.composite.ModelResults;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Set;

public class ToolingClientGradleConnection implements GradleConnectionInternal {
    private final Set<GradleBuildInternal> participants;
    private final File gradleUserHomeDir;
    private final File daemonBaseDir;
    private boolean closed;

    public ToolingClientGradleConnection(Set<GradleBuildInternal> participants, File gradleUserHomeDir, File daemonBaseDir) {
        this.participants = participants;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.daemonBaseDir = daemonBaseDir;
    }

    @Override
    public <T> ModelResults<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        return models(modelType).get();
    }

    @Override
    public <T> void getModels(Class<T> modelType, ResultHandler<? super ModelResults<T>> handler) throws IllegalStateException {
        models(modelType).get(handler);
    }

    @Override
    public <T> ModelBuilder<ModelResults<T>> models(Class<T> modelType) {
        checkOpen();
        checkSupportedModelType(modelType);
        return new ToolingClientCompositeModelBuilder<T>(modelType, CollectionUtils.collect(participants, new Transformer<GradleParticipantBuild, GradleBuildInternal>() {
            @Override
            public GradleParticipantBuild transform(GradleBuildInternal gradleBuildInternal) {
                return new GradleParticipantBuild(gradleBuildInternal, gradleUserHomeDir).withDaemonBaseDir(daemonBaseDir);
            }
        }));
    }

    @Override
    public BuildLauncher newBuild(BuildIdentity buildIdentity) {
        // TODO:DAZ These connections are not being closed
        for (GradleBuildInternal participant : participants) {
            if (participant.toBuildIdentity().equals(buildIdentity)) {
                return connect(participant).newBuild();
            }
        }

        throw new GradleConnectionException("Not a valid build identity: " + buildIdentity, new IllegalStateException("Build not part of composite"));
    }

    private ProjectConnection connect(GradleBuildInternal build) {
        return new GradleParticipantBuild(build, gradleUserHomeDir).connect();
    }

    private void checkOpen() {
        // TODO:DAZ Use ServiceLifecycle
        if (closed) {
            throw new IllegalStateException("Cannot use GradleConnection as it has been stopped.");
        }
    }

    private <T> void checkSupportedModelType(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
