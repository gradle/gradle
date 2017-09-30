/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata

class DefaultIvyModuleResolveMetadataTest extends AbstractModuleComponentResolveMetadataTest {
    @Override
    AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        return new DefaultIvyModuleResolveMetadata(new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, moduleDescriptor, configurations, dependencies, artifacts))
    }

    List<Artifact> artifacts = []

    def "builds and caches the configuration meta-data from the module descriptor"() {
        when:
        configuration("conf")

        then:
        metadata.getConfiguration("conf").transitive
        metadata.getConfiguration("conf").visible
    }

    def "builds and caches hierarchy for a configuration"() {
        given:
        configuration("a")
        configuration("b", ["a"])
        configuration("c", ["a"])
        configuration("d", ["b", "c"])

        when:
        def md = metadata

        then:
        md.getConfiguration("a").hierarchy == ["a"]
        md.getConfiguration("b").hierarchy == ["b", "a"]
        md.getConfiguration("c").hierarchy == ["c", "a"]
        md.getConfiguration("d").hierarchy == ["d", "b", "a", "c"]
    }

    def "builds and caches artifacts for a configuration"() {
        given:
        configuration("runtime")
        artifact("one", ["runtime"])
        artifact("two", ["runtime"])

        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.artifacts*.name.name == ["one", "two"]
        runtime.artifacts.is(runtime.artifacts)
    }

    def "each configuration contains a single variant containing no attributes and the artifacts of the configuration"() {
        given:
        configuration("runtime")
        artifact("one", ["runtime"])
        artifact("two", ["runtime"])

        when:
        def runtime = metadata.getConfiguration("runtime")

        then:
        runtime.variants.size() == 1
        runtime.variants.first().attributes.empty
        runtime.variants.first().artifacts*.name.name == ["one", "two"]
    }

    def "artifacts include union of those inherited from other configurations"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        artifact("one", ["runtime"])
        artifact("two", ["runtime", "compile"])
        artifact("three", ["compile"])

        when:
        def artifacts = metadata.getConfiguration("runtime").artifacts

        then:
        artifacts*.name.name == ["one", "two", "three"]
    }

    def "getBranch returns branch from moduleDescriptor" () {
        setup:
        moduleDescriptor.setBranch(expectedBranch)
        def metadataWithBranch = new DefaultIvyModuleResolveMetadata(new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, moduleDescriptor, [], [], []))

        expect:
        metadataWithBranch.branch == expectedBranch

        where:
        expectedBranch | _
        null           | _
        'someBranch'   | _
    }

    def artifact(String name, List<String> confs = []) {
        artifacts.add(new Artifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>))
    }

}
