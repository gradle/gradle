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
import org.apache.ivy.core.module.descriptor.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.internal.component.model.DependencyMetaData
import spock.lang.Specification

abstract class AbstractModuleComponentResolveMetaDataTest extends Specification {

    def id = Stub(ModuleVersionIdentifier)
    def componentId = Stub(ModuleComponentIdentifier)
    def moduleDescriptor = Mock(ModuleDescriptor)
    def metaData

    def setup() {
        metaData = createMetaData(id, moduleDescriptor, componentId)
    }

    abstract AbstractModuleComponentResolveMetaData createMetaData(ModuleVersionIdentifier id, ModuleDescriptor moduleDescriptor, ModuleComponentIdentifier componentIdentifier);

    def "has useful string representation"() {
        given:
        def config = Stub(Configuration)
        moduleDescriptor.getConfiguration('config') >> config
        componentId.getDisplayName() >> '<component>'

        expect:
        metaData.toString() == '<component>'
        metaData.getConfiguration('config').toString() == '<component>:config'
    }

    def "can replace identifiers"() {
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "version")

        given:
        metaData.setComponentId(newId)

        expect:
        metaData.componentId.is(newId)
        metaData.id.group == "group"
        metaData.id.name == "module"
        metaData.id.version == "version"
    }

    def "builds and caches the dependency meta-data from the module descriptor"() {
        def dependency1 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        def dependency2 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)

        given:
        moduleDescriptor.dependencies >> ([dependency1, dependency2] as DependencyDescriptor[])

        when:
        def deps = metaData.dependencies

        then:
        deps.size() == 2
        deps[0].descriptor == dependency1
        deps[1].descriptor == dependency2

        when:
        def deps2 = metaData.dependencies

        then:
        deps2.is(deps)

        and:
        0 * moduleDescriptor._
    }

    def "builds and caches the configuration meta-data from the module descriptor"() {
        when:
        def config = metaData.getConfiguration("conf")

        then:
        1 * moduleDescriptor.getConfiguration("conf") >> Stub(Configuration)

        when:
        def config2 = metaData.getConfiguration("conf")

        then:
        config2.is(config)

        and:
        0 * moduleDescriptor._
    }

    def "returns null for unknown configuration"() {
        given:
        moduleDescriptor.getConfiguration("conf") >> null

        expect:
        metaData.getConfiguration("conf") == null
    }

    def "builds and caches dependencies for a configuration"() {
        def config = Stub(Configuration)
        def parent = Stub(Configuration)
        def dependency1 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        dependency1.addDependencyConfiguration("conf", "a")
        def dependency2 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        dependency2.addDependencyConfiguration("*", "b")
        def dependency3 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        dependency3.addDependencyConfiguration("super", "c")
        def dependency4 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        dependency4.addDependencyConfiguration("other", "d")
        def dependency5 = new DefaultDependencyDescriptor(IvyUtil.createModuleRevisionId("org", "module", "1.2"), false)
        dependency5.addDependencyConfiguration("%", "e")

        given:
        moduleDescriptor.dependencies >> ([dependency1, dependency2, dependency3, dependency4, dependency5] as DependencyDescriptor[])
        moduleDescriptor.getConfiguration("conf") >> config
        moduleDescriptor.getConfiguration("super") >> parent
        config.extends >> ["super"]

        when:
        def dependencies = metaData.getConfiguration("conf").dependencies

        then:
        dependencies*.descriptor == [dependency1, dependency2, dependency3, dependency5]

        and:
        metaData.getConfiguration("conf").dependencies.is(dependencies)

        when:
        metaData.setDependencies([])

        then:
        metaData.getConfiguration("conf").dependencies == []
    }

    Artifact artifact(String name) {
        return Stub(Artifact) {
            getName() >> name
            getType() >> "type"
            getExt() >> "ext"
            getExtraAttributes() >> [classifier: "classifier"]
        }
    }

    def "builds and caches artifacts for a configuration"() {
        def artifact1 = artifact("one")
        def artifact2 = artifact("two")
        def config = Stub(Configuration)

        given:
        moduleDescriptor.allArtifacts >> ([artifact1, artifact2] as Artifact[])
        moduleDescriptor.configurationsNames >> ["conf"]
        moduleDescriptor.getArtifacts("conf") >> ([artifact1, artifact2] as Artifact[])
        moduleDescriptor.getConfiguration("conf") >> config

        when:
        def artifacts = metaData.getConfiguration("conf").artifacts

        then:
        artifacts*.name.name == ["one", "two"]

        and:
        metaData.getConfiguration("conf").artifacts.is(artifacts)
    }

    def "artifacts include union of those inherited from other configurations"() {
        def config = Stub(Configuration)
        def parent = Stub(Configuration)
        def artifact1 = artifact("one")
        def artifact2 = artifact("two")
        def artifact3 = artifact("three")

        given:
        moduleDescriptor.configurationsNames >> ["conf", "super"]
        moduleDescriptor.getConfiguration("conf") >> config
        moduleDescriptor.getConfiguration("super") >> parent
        config.extends >> ["super"]
        moduleDescriptor.allArtifacts >> ([artifact1, artifact2, artifact3] as Artifact[])
        moduleDescriptor.getArtifacts("conf") >> ([artifact1, artifact2] as Artifact[])
        moduleDescriptor.getArtifacts("super") >> ([artifact2, artifact3] as Artifact[])

        when:
        def artifacts = metaData.getConfiguration("conf").artifacts

        then:
        artifacts*.name.name == ["one", "two", "three"]
    }

    def "builds and caches exclude rules for a configuration"() {
        def rule1 = Stub(ExcludeRule)
        def rule2 = Stub(ExcludeRule)
        def rule3 = Stub(ExcludeRule)
        def config = Stub(Configuration)
        def parent = Stub(Configuration)

        given:
        rule1.configurations >> ["conf"]
        rule2.configurations >> ["super"]
        rule3.configurations >> ["other"]

        and:
        moduleDescriptor.getConfiguration("conf") >> config
        moduleDescriptor.getConfiguration("super") >> parent
        config.extends >> ["super"]
        moduleDescriptor.allExcludeRules >> ([rule1, rule2, rule3] as ExcludeRule[])

        when:
        def excludeRules = metaData.getConfiguration("conf").excludeRules

        then:
        excludeRules as List == [rule1, rule2]

        and:
        metaData.getConfiguration("conf").excludeRules.is(excludeRules)
    }

    def "can replace the dependencies for the module version"() {
        def dependency1 = Stub(DependencyMetaData)
        def dependency2 = Stub(DependencyMetaData)

        when:
        metaData.dependencies = [dependency1, dependency2]

        then:
        metaData.dependencies == [dependency1, dependency2]

        and:
        0 * moduleDescriptor._
    }
}
