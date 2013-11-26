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



package org.gradle.api.internal.artifacts.metadata

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.component.DefaultBuildComponentIdentifier
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import spock.lang.Specification

class ModuleDescriptorAdapterTest extends Specification {
    def id = Stub(ModuleVersionIdentifier)
    def moduleDescriptor = Mock(ModuleDescriptor)
    def metaData = new ModuleDescriptorAdapter(id, moduleDescriptor)

    def "has useful string representation"() {
        given:
        def config = Stub(Configuration)
        moduleDescriptor.getConfiguration('config') >> config
        id.toString() >> 'group:module:version'

        expect:
        metaData.toString() == 'group:module:version'
        metaData.getConfiguration('config').toString() == 'group:module:version:config'
    }

    def "builds and caches the dependency meta-data from the module descriptor"() {
        def dependency1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        def dependency2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)

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
        def dependency1 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        dependency1.addDependencyConfiguration("conf", "a")
        def dependency2 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        dependency2.addDependencyConfiguration("*", "b")
        def dependency3 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        dependency3.addDependencyConfiguration("super", "c")
        def dependency4 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
        dependency4.addDependencyConfiguration("other", "d")
        def dependency5 = new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org", "module", "1.2"), false)
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

    def "builds and caches artifacts from the module descriptor"() {
        def artifact1 = Stub(Artifact)
        def artifact2 = Stub(Artifact)

        given:
        moduleDescriptor.getAllArtifacts() >> ([artifact1, artifact2] as Artifact[])

        when:
        def artifacts = metaData.artifacts

        then:
        artifacts*.artifact == [artifact1, artifact2]

        and:
        metaData.artifacts.is(artifacts)
    }

    def "builds and caches artifacts for a configuration"() {
        def artifact1 = Stub(Artifact)
        def artifact2 = Stub(Artifact)
        def config = Stub(Configuration)

        given:
        moduleDescriptor.getConfiguration("conf") >> config
        moduleDescriptor.allArtifacts >> ([artifact1, artifact2] as Artifact[])
        moduleDescriptor.getArtifacts("conf") >> ([artifact1, artifact2] as Artifact[])

        when:
        def artifacts = metaData.getConfiguration("conf").artifacts

        then:
        artifacts*.artifact == [artifact1, artifact2]

        and:
        artifacts as List == metaData.artifacts as List

        and:
        metaData.getConfiguration("conf").artifacts.is(artifacts)
    }

    def "artifacts include union of those inherited from other configurations"() {
        def config = Stub(Configuration)
        def parent = Stub(Configuration)
        def artifact1 = Stub(Artifact)
        def artifact2 = Stub(Artifact)
        def artifact3 = Stub(Artifact)

        given:
        moduleDescriptor.getConfiguration("conf") >> config
        moduleDescriptor.getConfiguration("super") >> parent
        config.extends >> ["super"]
        moduleDescriptor.allArtifacts >> ([artifact1, artifact2, artifact3] as Artifact[])
        moduleDescriptor.getArtifacts("conf") >> ([artifact1, artifact2] as Artifact[])
        moduleDescriptor.getArtifacts("super") >> ([artifact2, artifact3] as Artifact[])

        when:
        def artifacts = metaData.getConfiguration("conf").artifacts

        then:
        artifacts*.artifact == [artifact1, artifact2, artifact3]
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

    def "can make a copy"() {
        def dependency1 = Stub(DependencyMetaData)
        def dependency2 = Stub(DependencyMetaData)

        given:
        metaData.changing = true
        metaData.metaDataOnly = true
        metaData.dependencies = [dependency1, dependency2]
        metaData.status = 'a'
        metaData.statusScheme = ['a', 'b', 'c']

        when:
        def copy = metaData.copy()

        then:
        copy != metaData
        copy.descriptor == moduleDescriptor
        copy.changing
        copy.metaDataOnly
        copy.dependencies == [dependency1, dependency2]
        copy.status == 'a'
        copy.statusScheme == ['a', 'b', 'c']
    }

    def "creates ModuleComponentIdentifier as component ID if not provided in constructor"() {
        when:
        ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier('group', 'name', 'version')
        def metaData = new ModuleDescriptorAdapter(moduleVersionIdentifier, moduleDescriptor)

        then:
        metaData.componentId == new DefaultModuleComponentIdentifier('group', 'name', 'version')
    }

    def "uses component ID if provided in constructor"() {
        when:
        ModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier('group', 'name', 'version')
        ComponentIdentifier componentIdentifier = new DefaultBuildComponentIdentifier(':myPath')
        def metaData = new ModuleDescriptorAdapter(moduleVersionIdentifier, moduleDescriptor, componentIdentifier)

        then:
        metaData.componentId == componentIdentifier
    }
}
