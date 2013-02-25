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

package org.gradle.api.publish.ivy.internal.publisher
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.CollectionUtils
import org.gradle.util.TextUtil
import spock.lang.Specification

class IvyDescriptorFileGeneratorTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    def projectIdentity = new DefaultIvyPublicationIdentity("my-org", "my-name", "my-version")
    IvyDescriptorFileGenerator generator = new IvyDescriptorFileGenerator(projectIdentity)

    def "writes correct prologue and schema declarations"() {
        expect:
        ivyFile.text.startsWith(TextUtil.toPlatformLineSeparators(
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
            info.@status.isEmpty()
            configurations.size() == 1
            configurations.conf.isEmpty()
            publications.size() == 1
            publications.artifacts.isEmpty()
            dependencies.size() == 1
            dependencies.dependency.isEmpty()
        }
    }

    def "encodes coordinates for XML and unicode"() {
        when:
        def projectIdentity = new DefaultIvyPublicationIdentity('org-ぴ₦ガき∆ç√∫', 'module-<tag attrib="value"/>-markup', 'version-&"')
        generator = new IvyDescriptorFileGenerator(projectIdentity)


        then:
        with (ivyXml) {
            info.@organisation == 'org-ぴ₦ガき∆ç√∫'
            info.@module == 'module-<tag attrib="value"/>-markup'
            info.@revision == 'version-&"'
        }
    }

    def "writes supplied status"() {
        when:
        generator.setStatus("my-status")

        then:
        ivyXml.info.@status == "my-status"
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
        def artifact1 = new DefaultIvyArtifact(null, "artifact1", "ext1", "type1", "classy")
        def artifact2 = new DefaultIvyArtifact(null, null, "", null, null)
        artifact2.setConf("runtime")
        generator.addArtifact(artifact1)
        generator.addArtifact(artifact2)

        then:
        includesMavenNamespace()
        and:
        with (ivyXml) {
            publications.artifact.size() == 2
            with (publications[0].artifact[0]) {
                it.@name == "artifact1"
                it.@type == "type1"
                it.@ext == "ext1"
                it.@classifier == "classy"
                it.@conf.isEmpty()
            }
            with (publications[0].artifact[1]) {
                it.@name.isEmpty()
                it.@type.isEmpty()
                it.@ext == ""
                it.@classifier.isEmpty()
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

        and:
        moduleDependency.artifacts >> new HashSet<DependencyArtifact>()
        moduleDependency.group >> "dep-group"
        moduleDependency.name >> "dep-name-2"
        moduleDependency.version >> "dep-version"

        and:
        generator.addDependency(new DefaultIvyDependency(projectDependency, "confMappingProject"))
        generator.addDependency(new DefaultIvyDependency(moduleDependency, null))

        then:
        with (ivyXml) {
            dependencies.dependency.size() == 2
            with (dependencies[0].dependency[0]) {
                it.@org == "dep-group"
                it.@name == "project-name"
                it.@rev == "dep-version"
                it.@conf == "confMappingProject"
            }
            with (dependencies[0].dependency[1]) {
                it.@org == "dep-group"
                it.@name == "dep-name-2"
                it.@rev == "dep-version"
                it.@conf.isEmpty()
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
        artifact1.name >> "artifact-1"
        artifact1.type >> "type-1"
        artifact1.extension >> "ext-1"
        artifact1.classifier >> null
        artifact2.name >> "artifact-2"
        artifact2.type >> null
        artifact2.classifier >> "classy"

        and:
        generator.addDependency(new DefaultIvyDependency(dependency, "confMapping"))

        then:
        includesMavenNamespace()

        and:
        with (ivyXml) {
            dependencies.dependency.size() == 1
            with (dependencies[0].dependency[0]) {
                it.@org == "dep-group"
                it.@name == "dep-name"
                it.@rev == "dep-version"
                it.@conf == "confMapping"

                artifact.size() == 2
                with (artifact[0]) {
                    it.@name == "artifact-1"
                    it.@type == "type-1"
                    it.@ext == "ext-1"
                    it.@classifier.isEmpty()
                }
                with (artifact[1]) {
                    it.@name == "artifact-2"
                    it.@type.isEmpty()
                    it.@ext.isEmpty()
                    it.@classifier == "classy"
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
                t.asNode().info[0].appendNode("description", "custom-description-ぴ₦ガき∆ç√∫")
            }
        })

        then:
        with (ivyXml) {
            info.@organisation == "my-org"
            info.@revision == "3"
            info.description == "custom-description-ぴ₦ガき∆ç√∫"
        }
    }

    private void includesMavenNamespace() {
        assert ivyFile.text.startsWith(TextUtil.toPlatformLineSeparators(
                """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="2.0" xmlns:m="http://ant.apache.org/ivy/maven">
"""))
    }

    private def getIvyXml() {
        return new XmlSlurper().parse(ivyFile);
    }

    private TestFile getIvyFile() {
        def ivyFile = testDirectoryProvider.testDirectory.file("ivy.xml")
        generator.writeTo(ivyFile)
        return ivyFile
    }
}
