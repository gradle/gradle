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
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.internal.component.local.model.LocalConfigurationMetaData
import org.gradle.internal.component.model.DependencyMetaData
import spock.lang.Specification

class DefaultIvyModulePublishMetaDataTest extends Specification {
    def metaData = new DefaultIvyModulePublishMetaData(Stub(ModuleVersionIdentifier), "status")

    def "can add artifacts"() {
        def artifact = Stub(Artifact)
        def file = new File("artifact.zip")

        when:
        metaData.addArtifact(artifact, file)

        then:
        metaData.artifacts.size() == 1
        def publishArtifact = metaData.artifacts.iterator().next()
        publishArtifact.artifact == artifact
        publishArtifact.file == file
    }

    def "can add configuration"() {
        when:
        metaData.addConfiguration("configName", "configDescription", ["one", "two", "three"] as Set, ["one", "two", "three", "configName"] as Set, true, true, null)

        then:
        metaData.moduleDescriptor.configurations.length == 1
        Configuration conf = metaData.moduleDescriptor.configurations[0]
        conf.name == "configName"
        conf.description == "configDescription"
        conf.extends as List == ["one", "three", "two"]
        conf.visibility == Configuration.Visibility.PUBLIC
        conf.transitive
    }

    def mockConfiguration() {
        return Stub(LocalConfigurationMetaData) { configuration ->
            configuration.name >> "configName"
            configuration.description >> "configDescription"
            configuration.extendsFrom >> (["one", "two", "three"] as Set)
            configuration.visible >> true
            configuration.transitive >> true
        }
    }

    def "can add dependencies"() {
        def dependency = Mock(DependencyMetaData)

        given:
        metaData.addConfiguration("configName", "configDescription", ["one", "two", "three"] as Set, ["one", "two", "three", "configName"] as Set, true, true, null)

        and:
        dependency.requested >> DefaultModuleVersionSelector.newSelector("group", "module", "version")
        dependency.force >> true
        dependency.changing >> true
        dependency.transitive >> true
        dependency.moduleConfigurations >> (["configName"] as String[])
        dependency.getDependencyConfigurations("configName", "configName") >> (["dep1"] as String[])
        dependency.artifacts >> ([] as Set)

        when:
        metaData.addDependency(dependency)

        then:
        metaData.moduleDescriptor.dependencies.length == 1
        def depDescriptor = metaData.moduleDescriptor.dependencies[0]
        depDescriptor.force
        depDescriptor.changing
        depDescriptor.transitive
        depDescriptor.moduleConfigurations as List == ["configName"]
        depDescriptor.allDependencyArtifacts.length == 0
    }
}
