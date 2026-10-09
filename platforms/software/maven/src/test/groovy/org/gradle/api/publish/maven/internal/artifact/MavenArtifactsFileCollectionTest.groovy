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

package org.gradle.api.publish.maven.internal.artifact

import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.publish.maven.MavenArtifact
import spock.lang.Specification

/**
 * Pins the decision boundary of {@link MavenArtifactsFileCollection#classify} (exercised via
 * the public {@code getPublicationArtifactSerializationEntries()}). Three cases:
 *
 * <ol>
 *     <li>An eager, non-{@link LazyPublishArtifact}-backed artifact (a {@link FileBasedMavenArtifact})
 *         → entry is a fixed-value provider wrapping the file directly.</li>
 *     <li>A {@link LazyPublishArtifact} whose provider reports {@code hasFixedValue()}
 *         (e.g. a {@code TaskProvider<AbstractArchiveTask>} — the Shadow shape)
 *         → entry is a fixed-value provider wrapping the file resolved via
 *         {@link LazyPublishArtifact#getFile()}. Pre-Option 1 this branch surfaced a bare
 *         {@code Task} reference to the CC serializer; the {@code hasFixedValue()} gate
 *         ensures that no longer happens.</li>
 *     <li>A {@link LazyPublishArtifact} whose provider is <em>not</em> settled at
 *         configuration time (e.g. issue #29253's {@code .map { … }} chain over a task output)
 *         → entry is the raw underlying provider, deferring resolution to task execution
 *         time.</li>
 * </ol>
 */
class MavenArtifactsFileCollectionTest extends Specification {

    private final TaskDependencyFactory taskDependencyFactory = DefaultTaskDependencyFactory.withNoAssociatedProject()
    private final FileResolver fileResolver = Mock(FileResolver)

    def "eager File-backed artifact is serialized as a fixed-value provider wrapping the file"() {
        given:
        File file = new File("eager.jar")
        MavenArtifact artifact = new FileBasedMavenArtifact(file, taskDependencyFactory)
        def collection = new MavenArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        entries[0].get() == file
    }

    def "LazyPublishArtifact whose provider is settled at configuration time is eagerly resolved to a File"() {
        given:
        // Providers.of(file) reports hasFixedValue() = true.
        File file = new File("eager-lazy.jar")
        MavenArtifact artifact = mavenArtifactFromProvider(Providers.of(file))
        def collection = new MavenArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        // The classify() fixed-value branch wraps the resolved file in a fresh Providers.of(...),
        // NOT the underlying LazyPublishArtifact.getProvider(). The two are distinct instances.
        entries[0].get() == file
    }

    def "LazyPublishArtifact whose provider is not settled is captured raw for lazy resolution"() {
        given:
        // Providers.notDefined() reports hasFixedValue() = false (isMissing() = true).
        // This is a proxy for the issue #29253 case where the terminal provider's
        // calculateExecutionTimeValue() returns a changing value.
        ProviderInternal<?> underlying = Providers.notDefined()
        MavenArtifact artifact = mavenArtifactFromProvider(underlying)
        def collection = new MavenArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        // The classify() changing-value branch returns the raw underlying provider, so the
        // entry is the same instance the LazyPublishArtifact was constructed with.
        entries[0].is(underlying)
    }

    private MavenArtifact mavenArtifactFromProvider(ProviderInternal<?> provider) {
        // The wrapper chain that MavenArtifactNotationParserFactory constructs at runtime
        // when the caller passes `artifact(someProvider)`:
        //   PublishArtifactBasedMavenArtifact -> DecoratingPublishArtifact -> LazyPublishArtifact.
        // Building it by hand here so the classify() unwrap logic gets exercised.
        LazyPublishArtifact lazy = new LazyPublishArtifact(provider, null, fileResolver, taskDependencyFactory)
        DecoratingPublishArtifact decorating = new DecoratingPublishArtifact(taskDependencyFactory, lazy)
        return new PublishArtifactBasedMavenArtifact(decorating, taskDependencyFactory)
    }
}
