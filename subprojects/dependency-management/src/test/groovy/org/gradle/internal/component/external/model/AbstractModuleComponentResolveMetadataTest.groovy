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
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ModuleSource
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

abstract class AbstractModuleComponentResolveMetadataTest extends Specification {

    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def moduleDescriptor = new MutableModuleDescriptorState(id, "status", false)

    abstract AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor);

    ModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, moduleDescriptor)
    }

    def "has useful string representation"() {
        given:
        configuration("config")

        expect:
        metadata.toString() == 'group:module:version'
        metadata.getConfiguration('config').toString() == 'group:module:version:config'
    }

    def "builds and caches the configuration meta-data from the module descriptor"() {
        when:
        configuration("conf")

        then:
        metadata.getConfiguration("conf").transitive
        metadata.getConfiguration("conf").visible
    }

    def "returns null for unknown configuration"() {
        expect:
        metadata.getConfiguration("conf") == null
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
        md.getConfiguration("a").hierarchy == ["a"] as Set
        md.getConfiguration("b").hierarchy == ["a", "b"] as Set
        md.getConfiguration("c").hierarchy == ["a", "c"] as Set
        md.getConfiguration("d").hierarchy == ["a", "b", "c", "d"] as Set
    }

    def "builds and caches dependencies for a configuration"() {
        given:
        configuration("super")
        configuration("conf", ["super"])
        dependency("org", "module", "1.1", "conf", "a")
        dependency("org", "module", "1.2", "*", "b")
        dependency("org", "module", "1.3", "super", "c")
        dependency("org", "module", "1.4", "other", "d")
        dependency("org", "module", "1.5", "%", "e")

        when:
        def md = metadata

        then:
        md.getConfiguration("conf").dependencies*.requested*.version == ["1.1", "1.2", "1.3", "1.5"]
        md.getConfiguration("super").dependencies*.requested*.version == ["1.2", "1.3", "1.5"]
    }

    def "builds and caches artifacts for a configuration"() {
        given:
        configuration("conf")
        artifact("one", ["conf"])
        artifact("two", ["conf"])

        when:
        def artifacts = metadata.getConfiguration("conf").artifacts

        then:
        artifacts*.name.name == ["one", "two"]
    }

    def "artifacts include union of those inherited from other configurations"() {

        given:
        configuration("super")
        configuration("conf", ["super"])
        artifact("one", ["conf"])
        artifact("two", ["conf", "super"])
        artifact("three", ["super"])

        when:
        def artifacts = metadata.getConfiguration("conf").artifacts

        then:
        artifacts*.name.name == ["one", "two", "three"]
    }

    def "builds and caches exclude rules for a configuration"() {
        given:
        configuration("super")
        configuration("conf", ["super"])
        def rule1 = exclude("one", ["conf"])
        def rule2 = exclude("two", ["super"])
        def rule3 = exclude("three", ["other"])

        when:
        def excludeRules = metadata.getConfiguration("conf").excludes

        then:
        excludeRules as List == [rule1, rule2]
    }

    def "can make a copy with different source"() {
        given:
        configuration("conf")
        def source = Stub(ModuleSource)

        when:
        def metadata = getMetadata()
        def copy = metadata.withSource(source)

        then:
        copy.source == source
        copy.configurationNames == ["conf"] as Set
        copy.getConfiguration("conf").is(metadata.getConfiguration("conf"))
        copy.dependencies.is(metadata.dependencies)
    }

    def configuration(String name, List<String> extendsFrom = []) {
        moduleDescriptor.addConfiguration(name, true, true, extendsFrom)
    }

    def dependency(String org, String module, String version) {
        moduleDescriptor.addDependency(new IvyDependencyMetadata(newSelector(org, module, version), ImmutableListMultimap.of()))
    }

    def dependency(String org, String module, String version, String fromConf, String toConf) {
        moduleDescriptor.addDependency(new IvyDependencyMetadata(newSelector(org, module, version), ImmutableListMultimap.of(fromConf, toConf)))
    }

    def artifact(String name, List<String> confs = []) {
        moduleDescriptor.addArtifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>)
    }

    def exclude(String name, List<String> confs = []) {
        def exclude = new DefaultExclude("group", name, confs as String[], "exact")
        moduleDescriptor.addExclude(exclude)
        exclude
    }

}
