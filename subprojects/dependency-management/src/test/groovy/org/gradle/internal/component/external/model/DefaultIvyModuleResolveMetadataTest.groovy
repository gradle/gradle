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
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.Exclude

class DefaultIvyModuleResolveMetadataTest extends AbstractModuleComponentResolveMetadataTest {
    @Override
    AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, configurations, dependencies, artifacts, excludes)
        return metadata.asImmutable()
    }

    List<Artifact> artifacts = []
    List<Exclude> excludes = []

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

    def "builds and caches exclude rules for a configuration"() {
        given:
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())
        configuration("compile")
        configuration("runtime", ["compile"])
        def rule1 = exclude("one", ["runtime"])
        def rule2 = exclude("two", ["compile"])
        def rule3 = exclude("three", ["other"])

        expect:
        def config = metadata.getConfiguration("runtime")

        def exclusions = config.getExclusions(moduleExclusions)
        exclusions == moduleExclusions.excludeAny(rule1, rule2)
        exclusions.is(config.getExclusions(moduleExclusions))
    }

    def exclude(String name, List<String> confs = []) {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", name), confs as String[], "exact")
        excludes.add(exclude)
        exclude
    }

    def artifact(String name, List<String> confs = []) {
        artifacts.add(new Artifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>))
    }

}
