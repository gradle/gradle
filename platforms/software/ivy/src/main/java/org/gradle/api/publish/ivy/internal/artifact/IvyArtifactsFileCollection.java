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
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
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
    public Iterable<ProviderInternal<File>> getPublicationArtifactSerializationEntries() {
        List<ProviderInternal<File>> entries = new ArrayList<>();
        for (IvyArtifact artifact : artifacts) {
            entries.add(classify(artifact));
        }
        return entries;
    }

    /**
     * Returns the serialization provider for the given artifact: either the underlying
     * {@link ProviderInternal} of a {@link LazyPublishArtifact} (so the codec can defer
     * resolution to task-execution time), or a fixed-value provider wrapping the resolved
     * {@link java.io.File} for eager artifacts.
     *
     * <p><strong>Scope.</strong> The lazy-provider fast-path only matches the internal
     * {@code PublishArtifactBasedIvyArtifact → DecoratingPublishArtifact → LazyPublishArtifact}
     * chain produced by {@code IvyArtifactNotationParserFactory} for {@code artifact(provider)}
     * calls. Third-party {@code PublishArtifact} implementations that are lazily backed by their
     * own provider machinery, but do not fit this exact chain, will fall through to eager
     * {@code artifact.getFile()} and reproduce the original CC-store failure. Fixing that class
     * of case is out of scope of issue #29253 and is tracked by the umbrella #24329.</p>
     */
    private static ProviderInternal<File> classify(IvyArtifact artifact) {
        if (artifact instanceof PublishArtifactBasedIvyArtifact) {
            PublishArtifact inner = ((PublishArtifactBasedIvyArtifact) artifact).getPublishArtifact();
            // Provider-based artifact notations are wrapped in DecoratingPublishArtifact by
            // PublishArtifactNotationParserFactory, which itself wraps a LazyPublishArtifact.
            if (inner instanceof DecoratingPublishArtifact) {
                inner = ((DecoratingPublishArtifact) inner).getPublishArtifact();
            }
            if (inner instanceof LazyPublishArtifact) {
                @SuppressWarnings("unchecked")
                var fileProvider = (ProviderInternal<File>)((LazyPublishArtifact) inner).getProvider();
                return fileProvider;
            }
        }
        return Providers.of(artifact.getFile());
    }
}
