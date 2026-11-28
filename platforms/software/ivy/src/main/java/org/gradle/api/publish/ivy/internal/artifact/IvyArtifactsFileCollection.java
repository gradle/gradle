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
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Files-view of an Ivy publication's artifact set that participates in configuration-cache
 * serialization via {@link PublicationArtifactSetFileCollection}. {@code getFiles()} eagerly
 * resolves each {@link IvyArtifact}'s file via {@link IvyArtifact#getFile()}; task
 * dependencies are the aggregate of every artifact's own build dependencies.
 */
@NullMarked
/* package */ class IvyArtifactsFileCollection extends AbstractFileCollection implements PublicationArtifactSetFileCollection {
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
        List<File> files = new ArrayList<>();
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
        return StreamSupport.stream(artifacts.spliterator(), false)
            .map(IvyArtifactsFileCollection::classify)
            .collect(Collectors.toList());
    }

    /**
     * Returns the serialization provider for the given artifact.
     * <p>
     * For eager artifacts (those whose file can be resolved at configuration time without
     * requiring a producing task to have run) the result is a fixed-value provider wrapping the
     * resolved {@link java.io.File}. This includes both non-{@link LazyPublishArtifact} artifacts
     * and {@link LazyPublishArtifact}s whose underlying provider reports {@code hasFixedValue()}
     * — notably archive-task-backed publications such as {@code artifact(tasks.shadowJar)}, whose
     * {@code TaskProvider<AbstractArchiveTask>} is settled at configuration time even though the
     * task has not yet run. For those, the pre-existing {@link LazyPublishArtifact#getFile()}
     * dispatch converts {@code Task → File} without ever surfacing the {@code Task} reference to
     * the configuration cache serializer.
     * <p>
     * For truly-changing artifacts (the issue-29253 pattern:
     * {@code artifact(taskOutput.map { … })} whose {@code TransformBackedProvider} would trip
     * {@code beforeRead} at store time) the result is the raw underlying provider, deferring
     * resolution to task execution time.
     * <p>
     * <strong>Scope.</strong> The lazy-provider fast-path only matches the internal
     * {@code PublishArtifactBasedIvyArtifact → DecoratingPublishArtifact → LazyPublishArtifact}
     * chain produced by {@code IvyArtifactNotationParserFactory} for {@code artifact(provider)}
     * calls. Third-party {@code PublishArtifact} implementations that are lazily backed by their
     * own provider machinery, but do not fit this exact chain, will fall through to eager
     * {@code artifact.getFile()} and reproduce the original CC-store failure. Fixing that class
     * of case is out of scope of issue #29253 and is tracked by the umbrella #24329.
     */
    private static ProviderInternal<File> classify(IvyArtifact artifact) {
        if (artifact instanceof PublishArtifactBasedIvyArtifact) {
            PublishArtifact inner = ((PublishArtifactBasedIvyArtifact) artifact).getPublishArtifact();
            // Provider-based artifact notations are wrapped in DecoratingPublishArtifact by
            // PublishArtifactNotationParserFactory, which itself wraps a LazyPublishArtifact.
            if (inner instanceof DecoratingPublishArtifact) {
                inner = ((DecoratingPublishArtifact) inner).getPublishArtifact();
            }
            if (inner instanceof LazyPublishArtifact lazy) {
                if (lazy.getProvider().calculateExecutionTimeValue().hasFixedValue()) {
                    // Invariant: `hasFixedValue()` reports true iff the provider's value chain
                    // does not require a producer task to have executed. Under that invariant,
                    // `lazy.getFile()` -> `getDelegate()` -> `provider.get()` is safe at
                    // configuration time — no `TransformBackedProvider.beforeRead` guard can
                    // trip, and the File / FileSystemLocation / Task / generic-Object dispatch
                    // inside `getDelegate()` will resolve to a File.
                    return Providers.of(lazy.getFile());
                } else {
                    // Provider still has changing content (e.g. issue #29253's `.map(...)`
                    // chain over a task output). Capture the raw provider; the codec defers
                    // resolution to task execution time.
                    @SuppressWarnings("unchecked")
                    var fileProvider = (ProviderInternal<File>) lazy.getProvider();
                    return fileProvider;
                }
            }
        }
        return Providers.of(artifact.getFile());
    }
}
