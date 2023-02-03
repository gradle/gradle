/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.artifact;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public class PublishArtifactBasedIvyArtifact extends AbstractIvyArtifact {
    private final PublishArtifact artifact;
    private final IvyPublicationIdentity identity;

    public PublishArtifactBasedIvyArtifact(PublishArtifact artifact, IvyPublicationIdentity identity, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory);
        this.artifact = artifact;
        this.identity = identity;
    }

    @Override
    protected String getDefaultName() {
        return identity.getModule();
    }

    @Override
    protected String getDefaultType() {
        return artifact.getType();
    }

    @Override
    protected String getDefaultExtension() {
        return artifact.getExtension();
    }

    @Override
    protected String getDefaultClassifier() {
        return artifact.getClassifier();
    }

    @Override
    protected String getDefaultConf() {
        return null;
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return artifact.getBuildDependencies();
    }

    @Override
    public File getFile() {
        return artifact.getFile();
    }

    @Override
    public boolean shouldBePublished() {
        return PublishArtifactInternal.shouldBePublished(artifact);
    }
}
