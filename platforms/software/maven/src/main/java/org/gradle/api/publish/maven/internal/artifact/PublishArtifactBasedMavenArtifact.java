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

package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public class PublishArtifactBasedMavenArtifact extends AbstractMavenArtifact {
    private final PublishArtifact publishArtifact;

    public PublishArtifactBasedMavenArtifact(PublishArtifact publishArtifact, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory);
        this.publishArtifact = publishArtifact;
    }

    @Override
    public File getFile() {
        return publishArtifact.getFile();
    }

    @Override
    protected String getDefaultExtension() {
        return publishArtifact.getExtension();
    }

    @Override
    protected String getDefaultClassifier() {
        return publishArtifact.getClassifier();
    }

    @Override
    protected TaskDependency getDefaultBuildDependencies() {
        return publishArtifact.getBuildDependencies();
    }

    @Override
    public boolean shouldBePublished() {
        return PublishArtifactInternal.shouldBePublished(publishArtifact);
    }
}
