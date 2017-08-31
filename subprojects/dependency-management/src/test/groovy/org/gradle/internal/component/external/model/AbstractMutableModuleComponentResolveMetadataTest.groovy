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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

abstract class AbstractMutableModuleComponentResolveMetadataTest extends Specification {
    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def moduleDescriptor = new MutableModuleDescriptorState(id, "status", false)
    def configurations = []
    def dependencies = []

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor, List<Configuration> configurations, List<DependencyMetadata> dependencies)

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, Set<IvyArtifactName> artifacts);

    MutableModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, moduleDescriptor, configurations, dependencies)
    }

    def "can replace identifiers"() {
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = getMetadata()

        given:
        metadata.componentId = newId

        expect:
        metadata.componentId == newId
        metadata.id == DefaultModuleVersionIdentifier.newId(newId)
        metadata.asImmutable().componentId == newId
        metadata.asImmutable().asMutable().componentId == newId
    }

    def "builds and caches the dependency meta-data from the module descriptor"() {
        given:
        dependency("org", "module", "1.2")
        dependency("org", "another", "1.2")

        when:
        def deps = metadata.dependencies

        then:
        deps.size() == 2
        deps[0].requested == newSelector("org", "module", "1.2")
        deps[1].requested == newSelector("org", "another", "1.2")

        and:
        def immutable = metadata.asImmutable()
        immutable.dependencies.size() == 2
        immutable.dependencies[0].requested == newSelector("org", "module", "1.2")
        immutable.dependencies[1].requested == newSelector("org", "another", "1.2")

        and:
        def copy = immutable.asMutable()
        copy.dependencies.size() == 2
        copy.dependencies[0].requested == newSelector("org", "module", "1.2")
        copy.dependencies[1].requested == newSelector("org", "another", "1.2")
    }

    def "can create default metadata"() {
        def artifact1 = Stub(IvyArtifactName)
        def artifact2 = Stub(IvyArtifactName)

        def metadata = createMetadata(id, [artifact1, artifact2] as Set)

        expect:
        metadata.componentId == id
        metadata.dependencies.empty

        def immutable = metadata.asImmutable()
        immutable.componentId == id
        immutable.generated
        immutable.getConfiguration("default")
        immutable.getConfiguration("default").artifacts.collect { it.name } == [artifact1, artifact2]
        immutable.dependencies.empty
    }

    def "can replace the dependencies for the module"() {
        def dependency1 = Stub(DependencyMetadata)
        def dependency2 = Stub(DependencyMetadata)

        when:
        dependency("foo", "bar", "1.0")
        def metadata = getMetadata()

        then:
        metadata.dependencies*.requested*.toString() == ["foo:bar:1.0"]

        when:
        metadata.dependencies = [dependency1, dependency2]

        then:
        metadata.dependencies == [dependency1, dependency2]
    }

    def "can replace the artifacts for the module version"() {
        when:
        configuration("runtime")
        artifact("ignore-me", "runtime")
        def metadata = getMetadata()
        def a1 = metadata.artifact("jar", "jar", null)
        def a2 = metadata.artifact("pom", "pom", null)
        metadata.artifacts = [a1, a2]

        then:
        def immutable = metadata.asImmutable()
        immutable.artifacts == [a1, a2]
        immutable.getConfiguration("runtime").artifacts == [a1, a2] as Set

        def copy = immutable.asMutable()
        copy.artifacts == [a1, a2]
    }

    def dependency(String org, String module, String version) {
        dependencies.add(new IvyDependencyMetadata(newSelector(org, module, version), ImmutableListMultimap.of()))
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def artifact(String name, String... confs) {
        moduleDescriptor.addArtifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>)
    }
}
