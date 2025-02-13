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

import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;

public class PublishArtifactBasedMavenArtifact extends AbstractMavenArtifact {
    private final PublishArtifactInternal publishArtifact;

    public PublishArtifactBasedMavenArtifact(PublishArtifactInternal publishArtifact, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory, publishArtifact);
        this.publishArtifact = publishArtifact;
    }

    @Override
    public Provider<? extends FileSystemLocation> getFileProvider() {
        return publishArtifact.getFileProvider();
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
    public boolean shouldBePublished() {
        return PublishArtifactInternal.shouldBePublished(publishArtifact);
    }
}
