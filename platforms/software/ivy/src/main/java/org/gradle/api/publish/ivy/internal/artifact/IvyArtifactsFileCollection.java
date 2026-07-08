/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.internal.artifact.PublicationArtifactSetFileCollection;
import org.gradle.api.publish.ivy.IvyArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Files-view of an Ivy publication's artifact set that participates in configuration-cache
 * serialization via {@link PublicationArtifactSetFileCollection}. Query-time behavior
 * ({@code getFiles()}, task dependencies) matches the prior {@code MinimalFileSet}-based
 * implementation.
 */
class IvyArtifactsFileCollection extends AbstractFileCollection implements PublicationArtifactSetFileCollection {

    private final String publicationName;
    private final Iterable<IvyArtifact> artifacts;

    IvyArtifactsFileCollection(String publicationName, Iterable<IvyArtifact> artifacts, TaskDependencyFactory taskDependencyFactory) {
        super(taskDependencyFactory);
        this.publicationName = publicationName;
        this.artifacts = artifacts;
    }

    @Override
    public String getDisplayName() {
        return "artifacts for Ivy publication '" + publicationName + "'";
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        Set<File> files = new LinkedHashSet<>();
        for (IvyArtifact artifact : artifacts) {
            files.add(artifact.getFile());
        }
        visitor.visitCollection(OTHER, files);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        for (IvyArtifact artifact : artifacts) {
            context.add(artifact);
        }
    }

    @Override
    public Iterable<Object> getPublicationArtifactSerializationEntries() {
        List<Object> entries = new ArrayList<>();
        for (IvyArtifact artifact : artifacts) {
            entries.add(classify(artifact));
        }
        return entries;
    }

    private static Object classify(IvyArtifact artifact) {
        if (artifact instanceof PublishArtifactBasedIvyArtifact) {
            PublishArtifact inner = ((PublishArtifactBasedIvyArtifact) artifact).getPublishArtifact();
            if (inner instanceof LazyPublishArtifact) {
                return ((LazyPublishArtifact) inner).getProvider();
            }
        }
        return artifact.getFile();
    }
}
