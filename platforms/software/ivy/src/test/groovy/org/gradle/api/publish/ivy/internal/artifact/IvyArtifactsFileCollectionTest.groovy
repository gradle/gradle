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

package org.gradle.api.publish.ivy.internal.artifact

import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import spock.lang.Specification

/**
 * Pins the decision boundary of {@link IvyArtifactsFileCollection#classify} (exercised via
 * the public {@code getPublicationArtifactSerializationEntries()}). Mirrors
 * {@code MavenArtifactsFileCollectionTest} — the classify logic is structurally identical.
 * See that class for the design rationale.
 */
class IvyArtifactsFileCollectionTest extends Specification {

    private final TaskDependencyFactory taskDependencyFactory = DefaultTaskDependencyFactory.withNoAssociatedProject()
    private final FileResolver fileResolver = Mock(FileResolver)
    private final IvyPublicationCoordinates coordinates = coordinates()

    def "eager File-backed artifact is serialized as a fixed-value provider wrapping the file"() {
        given:
        File file = new File("eager.jar")
        IvyArtifact artifact = new FileBasedIvyArtifact(file, coordinates, taskDependencyFactory)
        def collection = new IvyArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        entries[0].get() == file
    }

    def "LazyPublishArtifact whose provider is settled at configuration time is eagerly resolved to a File"() {
        given:
        File file = new File("eager-lazy.jar")
        IvyArtifact artifact = ivyArtifactFromProvider(Providers.of(file))
        def collection = new IvyArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        entries[0].get() == file
    }

    def "LazyPublishArtifact whose provider is not settled is captured raw for lazy resolution"() {
        given:
        ProviderInternal<?> underlying = Providers.notDefined()
        IvyArtifact artifact = ivyArtifactFromProvider(underlying)
        def collection = new IvyArtifactsFileCollection("test", [artifact], taskDependencyFactory)

        when:
        List<ProviderInternal<File>> entries = collection.getPublicationArtifactSerializationEntries().toList()

        then:
        entries.size() == 1
        entries[0].is(underlying)
    }

    private IvyArtifact ivyArtifactFromProvider(ProviderInternal<?> provider) {
        LazyPublishArtifact lazy = new LazyPublishArtifact(provider, null, fileResolver, taskDependencyFactory)
        DecoratingPublishArtifact decorating = new DecoratingPublishArtifact(taskDependencyFactory, lazy)
        return new PublishArtifactBasedIvyArtifact(decorating, coordinates, taskDependencyFactory)
    }

    private IvyPublicationCoordinates coordinates() {
        Property<String> organisation = Mock(Property) { get() >> "test-org" }
        Property<String> module = Mock(Property) { get() >> "test-module" }
        Property<String> revision = Mock(Property) { get() >> "1.0" }
        return Mock(IvyPublicationCoordinates) {
            getOrganisation() >> organisation
            getModule() >> module
            getRevision() >> revision
        }
    }
}
