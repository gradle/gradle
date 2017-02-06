/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

abstract class AbstractModuleComponentResolveMetadataTest extends Specification {

    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def moduleDescriptor = new MutableModuleDescriptorState(id, "status", false)
    def configurations = []
    def dependencies = []

    abstract AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor, List<Configuration> configurations, List<DependencyMetadata> dependencies)

    ModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, moduleDescriptor, configurations, dependencies)
    }

    def "has useful string representation"() {
        given:
        configuration("runtime")

        expect:
        metadata.toString() == 'group:module:version'
        metadata.getConfiguration('runtime').toString() == 'group:module:version:runtime'
    }

    def "returns null for unknown configuration"() {
        expect:
        metadata.getConfiguration("conf") == null
    }

    def "builds and caches dependencies for a configuration"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        dependency("org", "module", "1.1", "runtime", "a")
        dependency("org", "module", "1.2", "*", "b")
        dependency("org", "module", "1.3", "compile", "c")
        dependency("org", "module", "1.4", "other", "d")
        dependency("org", "module", "1.5", "%", "e")

        when:
        def md = metadata
        def runtime = md.getConfiguration("runtime")
        def compile = md.getConfiguration("compile")

        then:
        runtime.dependencies*.requested*.version == ["1.1", "1.2", "1.3", "1.5"]
        runtime.dependencies.is(runtime.dependencies)

        compile.dependencies*.requested*.version == ["1.2", "1.3", "1.5"]
        compile.dependencies.is(compile.dependencies)
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

    def "can make a copy with different source"() {
        given:
        configuration("compile")
        def source = Stub(ModuleSource)

        when:
        def metadata = getMetadata()
        def copy = metadata.withSource(source)

        then:
        copy.source == source
        copy.configurationNames == metadata.configurationNames
        copy.getConfiguration("compile").is(metadata.getConfiguration("compile"))
        copy.dependencies.is(metadata.dependencies)
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def dependency(String org, String module, String version) {
        dependencies.add(new IvyDependencyMetadata(newSelector(org, module, version), ImmutableListMultimap.of()))
    }

    def dependency(String org, String module, String version, String fromConf, String toConf) {
        dependencies.add(new IvyDependencyMetadata(newSelector(org, module, version), ImmutableListMultimap.of(fromConf, toConf)))
    }

    def artifact(String name, List<String> confs = []) {
        moduleDescriptor.addArtifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>)
    }

    def exclude(String name, List<String> confs = []) {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", name), confs as String[], "exact")
        moduleDescriptor.addExclude(exclude)
        exclude
    }

}
