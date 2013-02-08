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

package org.gradle.api.publish.ivy.internal.tasks

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.Module
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.publish.ivy.internal.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.util.CollectionUtils
import org.gradle.util.TextUtil
import spock.lang.Specification

class IvyDescriptorFileGeneratorTest extends Specification {
    Module module = new DefaultModule("my-org", "my-name", "my-version", "my-status")
    IvyDescriptorFileGenerator generator = new IvyDescriptorFileGenerator(module)

    def "writes correct prologue and schema declarations"() {
        expect:
        ivyFileContent.startsWith(TextUtil.toPlatformLineSeparators(
"""<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="2.0">
"""))
    }

    def "writes empty descriptor with module values"() {
        expect:
        with (ivyXml) {
            info.@organisation == "my-org"
            info.@module == "my-name"
            info.@revision == "my-version"
            info.@status == "my-status"
            configurations.isEmpty()
            publications.isEmpty()
            dependencies.isEmpty()
        }
    }

    def "writes supplied configurations"() {
        when:
        def config1 = new DefaultIvyConfiguration("config1")
        def config2 = new DefaultIvyConfiguration("config2")
        config1.extend("foo")
        config1.extend("bar")
        generator.addConfiguration(config1)
        generator.addConfiguration(config2)

        then:
        with (ivyXml) {
            configurations.conf.size() == 2
            with (configurations[0].conf[0]) {
                it.@name == "config1"
                it.@extends == "foo,bar"
            }
            with (configurations[0].conf[1]) {
                it.@name == "config2"
                it.@extends.empty
            }
        }
    }
    def "writes supplied publication artifacts"() {
        when:
        def artifact1 = new DefaultIvyArtifact(null, "artifact1", "ext1", "type1")
        def artifact2 = new DefaultIvyArtifact(null, "artifact2", null, null)
        artifact2.setConf("runtime")
        generator.addArtifact(artifact1)
        generator.addArtifact(artifact2)

        then:
        with (ivyXml) {
            publications.artifact.size() == 2
            with (publications[0].artifact[0]) {
                it.@name == "artifact1"
                it.@type == "type1"
                it.@ext == "ext1"
                it.@conf.isEmpty()
            }
            with (publications[0].artifact[1]) {
                it.@name == "artifact2"
                it.@type.isEmpty()
                it.@ext.isEmpty()
                it.@conf == "runtime"
            }
        }
    }

    def "writes supplied dependencies"() {
        def projectDependency = Mock(ProjectDependency)
        def moduleDependency = Mock(ModuleDependency)
        when:
        projectDependency.artifacts >> new HashSet<DependencyArtifact>()
        projectDependency.group >> "dep-group"
        projectDependency.name >> "dep-name-1"
        projectDependency.version >> "dep-version"
        projectDependency.dependencyProject >> Stub(Project) {
            getName() >> "project-name"
        }
        projectDependency.configuration >> "default"

        and:
        moduleDependency.artifacts >> new HashSet<DependencyArtifact>()
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name-2"
        moduleDependency.version >> "dep-version"
        moduleDependency.configuration >> "dep-conf"

        and:
        generator.addRuntimeDependency(projectDependency)
        generator.addRuntimeDependency(moduleDependency)

        then:
        with (ivyXml) {
            dependencies.dependency.size() == 2
            with (dependencies[0].dependency[0]) {
                it.@org == "dep-group"
                it.@name == "project-name"
                it.@rev == "dep-version"
                it.@conf == "runtime->default"
            }
            with (dependencies[0].dependency[1]) {
                it.@org == "dep-group"
                it.@name == "dep-name-2"
                it.@rev == "dep-version"
                it.@conf == "runtime->dep-conf"
            }
        }
    }

    def "writes dependency with artifacts"() {
        def dependency = Mock(ModuleDependency)
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)

        when:
        dependency.artifacts >> CollectionUtils.toSet([artifact1, artifact2])
        dependency.group >> "dep-group"
        dependency.name >> "dep-name"
        dependency.version >> "dep-version"
        dependency.configuration >> "default"
        artifact1.name >> "artifact-1"
        artifact1.type >> "type-1"
        artifact1.extension >> "ext-1"
        artifact2.name >> "artifact-2"
        artifact2.type >> null
        artifact2.classifier >> null

        and:
        generator.addRuntimeDependency(dependency)

        then:
        with (ivyXml) {
            dependencies.dependency.size() == 1
            with (dependencies[0].dependency[0]) {
                it.@org == "dep-group"
                it.@name == "dep-name"
                it.@rev == "dep-version"
                it.@conf == "runtime->default"

                artifact.size() == 2
                with (artifact[0]) {
                    it.@name == "artifact-1"
                    it.@type == "type-1"
                    it.@ext == "ext-1"
                    it.@conf.isEmpty()
                }
                with (artifact[1]) {
                    it.@name == "artifact-2"
                    it.@type.isEmpty()
                    it.@ext.isEmpty()
                    it.@conf.isEmpty()
                }
            }
        }
    }

    def "applies withXml actions"() {
        when:
        generator.withXml(new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().info[0].@revision = "3"
            }
        })
        generator.withXml(new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().info[0].appendNode("description", "custom-description")
            }
        })

        then:
        with (ivyXml) {
            info.@organisation == "my-org"
            info.@revision == "3"
            info.description == "custom-description"
        }
    }

    private def getIvyXml() {
        return new XmlSlurper().parse(new StringReader(ivyFileContent));
    }

    private String getIvyFileContent() {
        def writer = new StringWriter()
        generator.write(writer)
        return writer.toString()
    }
}
