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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

abstract class AbstractModuleComponentResolveMetaDataTest extends Specification {

    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def moduleDescriptor = new MutableModuleDescriptorState(id, "status", false)

    abstract AbstractModuleComponentResolveMetaData createMetaData(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor);

    MutableModuleComponentResolveMetaData getMetaData() {
        return createMetaData(id, moduleDescriptor)
    }

    def "has useful string representation"() {
        given:
        configuration("config")

        expect:
        metaData.toString() == 'group:module:version'
        metaData.getConfiguration('config').toString() == 'group:module:version:config'
    }

    def "can replace identifiers"() {
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metaData = getMetaData()

        given:
        metaData.setComponentId(newId)

        expect:
        metaData.componentId.is(newId)
        metaData.id.group == "group"
        metaData.id.name == "module"
        metaData.id.version == "version"
    }

    def "builds and caches the dependency meta-data from the module descriptor"() {
        given:
        dependency("org", "module", "1.2")
        dependency("org", "another", "1.2")

        when:
        def deps = metaData.dependencies

        then:
        deps.size() == 2
        deps[0].requested == newSelector("org", "module", "1.2")
        deps[1].requested == newSelector("org", "another", "1.2")
    }

    def "builds and caches the configuration meta-data from the module descriptor"() {
        when:
        configuration("conf")

        then:
        metaData.getConfiguration("conf").transitive
        metaData.getConfiguration("conf").visible
    }

    def "returns null for unknown configuration"() {
        expect:
        metaData.getConfiguration("conf") == null
    }

    def "builds and caches dependencies for a configuration"() {
        given:
        configuration("super")
        configuration("conf", ["super"])
        dependency("org", "module", "1.1").addDependencyConfiguration("conf", "a")
        dependency("org", "module", "1.2").addDependencyConfiguration("*", "b")
        dependency("org", "module", "1.3").addDependencyConfiguration("super", "c")
        dependency("org", "module", "1.4").addDependencyConfiguration("other", "d")
        dependency("org", "module", "1.5").addDependencyConfiguration("%", "e")

        when:
        def dependencies = metaData.getConfiguration("conf").dependencies

        then:
        dependencies*.requested*.version == ["1.1", "1.2", "1.3", "1.5"]

        when:
        def metaData = getMetaData()
        metaData.setDependencies([])

        then:
        metaData.getConfiguration("conf").dependencies == []
    }

    def "builds and caches artifacts for a configuration"() {
        given:
        configuration("conf")
        artifact("one", ["conf"])
        artifact("two", ["conf"])

        when:
        def artifacts = metaData.getConfiguration("conf").artifacts

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
        def artifacts = metaData.getConfiguration("conf").artifacts

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
        def excludeRules = metaData.getConfiguration("conf").excludes

        then:
        excludeRules as List == [rule1, rule2]
    }

    def "can replace the dependencies for the module version"() {
        def dependency1 = Stub(DependencyMetaData)
        def dependency2 = Stub(DependencyMetaData)

        when:
        dependency("foo", "bar", "1.0")
        def metaData = getMetaData()

        then:
        metaData.dependencies*.requested*.toString() == ["foo:bar:1.0"]

        when:
        metaData.dependencies = [dependency1, dependency2]

        then:
        metaData.dependencies == [dependency1, dependency2]
    }

    def configuration(String name, List<String> extendsFrom = []) {
        moduleDescriptor.addConfiguration(name, true, true, extendsFrom)
    }

    def dependency(String org, String module, String version) {
        moduleDescriptor.addDependency(newSelector(org, module, version))
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
