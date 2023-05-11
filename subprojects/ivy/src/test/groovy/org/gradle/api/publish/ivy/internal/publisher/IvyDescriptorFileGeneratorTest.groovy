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

import groovy.xml.XmlSlurper
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.ivy.internal.artifact.FileBasedIvyArtifact
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyModuleDescriptorAuthor
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyModuleDescriptorDescription
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyModuleDescriptorLicense
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

import javax.xml.namespace.QName

import static org.gradle.util.TestUtil.objectFactory

class IvyDescriptorFileGeneratorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    VersionMappingStrategyInternal versionMappingStrategy = Mock() {
        findStrategyForVariant(_) >> Mock(VariantVersionMappingStrategyInternal)
    }

    def projectIdentity = new DefaultIvyPublicationIdentity("my-org", "my-name", "my-version")
    IvyDescriptorFileGenerator generator = new IvyDescriptorFileGenerator(projectIdentity, false, versionMappingStrategy)

    def "writes correct prologue and schema declarations"() {
        expect:
        ivyFile.text.startsWith(TextUtil.toPlatformLineSeparators(
"""<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="2.0">
"""))
    }

    def "writes Gradle metadata marker"() {
        given:
        generator = new IvyDescriptorFileGenerator(projectIdentity, markerPresent, versionMappingStrategy)

        expect:
        ivyFile.text.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) == markerPresent

        where:
        markerPresent << [true, false]
    }

    def "writes empty descriptor with module values"() {
        expect:
        with (ivyXml) {
            info.@organisation == "my-org"
            info.@module == "my-name"
            info.@revision == "my-version"
            info.@status.isEmpty()
            info.@branch.isEmpty()
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
        generator = new IvyDescriptorFileGenerator(projectIdentity, false, null)


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

    def "writes supplied branch"() {
        when:
        generator.setBranch("someBranch")

        then:
        ivyXml.info.@branch == "someBranch"
    }

    def "writes supplied licenses" () {
        given:
        def objectFactory = objectFactory()
        def license1 = new DefaultIvyModuleDescriptorLicense(objectFactory)
        license1.name.set("EPL v2.0")
        def license2 = new DefaultIvyModuleDescriptorLicense(objectFactory)
        license2.name.set("Apache v2.0")
        license2.url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")

        when:
        generator.addLicense(license1)
        generator.addLicense(license2)

        then:
        with (ivyXml.info) {
            license.size() == 2
            license[0].@name == "EPL v2.0"
            license[0].@url.isEmpty()
            license[1].@name == "Apache v2.0"
            license[1].@url == "http://www.apache.org/licenses/LICENSE-2.0.txt"
        }
    }

    def "writes supplied authors" () {
        given:
        def objectFactory = objectFactory()
        def author1 = new DefaultIvyModuleDescriptorAuthor(objectFactory)
        author1.name.set("Alice")
        def author2 = new DefaultIvyModuleDescriptorAuthor(objectFactory)
        author2.name.set("Bob")
        author2.url.set("http://example.com/bob/")

        when:
        generator.addAuthor(author1)
        generator.addAuthor(author2)

        then:
        with (ivyXml.info) {
            ivyauthor.size() == 2
            ivyauthor[0].@name == "Alice"
            ivyauthor[0].@url.isEmpty()
            ivyauthor[1].@name == "Bob"
            ivyauthor[1].@url == "http://example.com/bob/"
        }
    }

    def "writes supplied description" () {
        given:
        def description = new DefaultIvyModuleDescriptorDescription(objectFactory())
        description.text.set("Some lengthy description.")
        description.homepage.set("http://example.com")

        when:
        generator.description = description

        then:
        with (ivyXml) {
            info.description[0].text() == "Some lengthy description."
            info.description[0].@homepage == "http://example.com"
        }
    }

    def "writes supplied description without text" () {
        given:
        def description = new DefaultIvyModuleDescriptorDescription(objectFactory())
        description.homepage.set("http://example.com")

        when:
        generator.description = description

        then:
        with (ivyXml) {
            info.description[0].text() == ""
            info.description[0].@homepage == "http://example.com"
        }
    }

    def "writes supplied extra info elements" () {
        when:
        generator.setExtraInfo([(ns('foo')): 'fooValue', (ns('bar')): 'barValue'])

        then:
        ivyXml.info."foo".size() == 1
        ivyXml.info."foo"[0].namespaceURI() == ns('foo').namespaceURI
        ivyXml.info."foo"[0].text() == 'fooValue'
        ivyXml.info."bar".size() == 1
        ivyXml.info."bar"[0].namespaceURI() == ns('bar').namespaceURI
        ivyXml.info."bar"[0].text() == 'barValue'
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
        def artifact1 = new FileBasedIvyArtifact(new File("foo.txt"), new DefaultIvyPublicationIdentity("org", "module", "rev"), DefaultTaskDependencyFactory.withNoAssociatedProject())
        artifact1.classifier = "classy"
        def artifact2 = new FileBasedIvyArtifact(new File("foo"), new DefaultIvyPublicationIdentity("", "", ""), DefaultTaskDependencyFactory.withNoAssociatedProject())
        artifact2.setConf("runtime")
        generator.addArtifact(artifact1.asNormalisedArtifact())
        generator.addArtifact(artifact2.asNormalisedArtifact())

        then:
        includesMavenNamespace()
        and:
        with (ivyXml) {
            publications.artifact.size() == 2
            with (publications[0].artifact[0]) {
                it.@name == "module"
                it.@type == "txt"
                it.@ext == "txt"
                it."@m:classifier" == "classy"
                it.@conf.isEmpty()
            }
            with (publications[0].artifact[1]) {
                it.@name == ""
                it.@type == ""
                it.@ext == ""
                it."@m:classifier".isEmpty()
                it.@conf == "runtime"
            }
        }
    }

    def "writes supplied dependencies"() {
        when:
        generator.addDependency(new DefaultIvyDependency('dep-group', 'dep-name-1', 'dep-version', "confMappingProject", true))
        generator.addDependency(new DefaultIvyDependency('dep-group', 'dep-name-2', 'dep-version', null, true))

        then:
        with (ivyXml) {
            dependencies.dependency.size() == 2
            with (dependencies[0].dependency[0]) {
                it.@org == "dep-group"
                it.@name == "dep-name-1"
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
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)

        when:
        artifact1.name >> "artifact-1"
        artifact1.type >> "type-1"
        artifact1.extension >> "ext-1"
        artifact1.classifier >> null
        artifact2.name >> "artifact-2"
        artifact2.type >> null
        artifact2.classifier >> "classy"

        and:
        generator.addDependency(new DefaultIvyDependency('dep-group', 'dep-name', 'dep-version', "confMapping", true, [artifact1, artifact2]))

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
                    it."@m:classifier".isEmpty()
                }
                with (artifact[1]) {
                    it.@name == "artifact-2"
                    it.@type.isEmpty()
                    it.@ext.isEmpty()
                    it."@m:classifier" == "classy"
                }
            }
        }
    }

    def "writes dependency with exclusion"() {
        def exclude1 = Mock(ExcludeRule) {
            getGroup() >> 'excludeGroup1'
            getModule() >> 'excludeModule1'
        }
        def exclude2 = Mock(ExcludeRule) {
            getGroup() >> 'excludeGroup2'
        }
        def exclude3 = Mock(ExcludeRule) {
            getModule() >> 'excludeModule3'
        }


        when:
        generator.addDependency(new DefaultIvyDependency('dep-group', 'dep-name-1', 'dep-version', "confMappingProject", true, [], [exclude1, exclude2, exclude3]))

        then:
        with (ivyXml) {
            dependencies[0].dependency[0].exclude.size() == 3
            with (dependencies[0].dependency[0]) {
                with(exclude[0]) {
                    it.@org == 'excludeGroup1'
                    it.@module == 'excludeModule1'
                }
                with(exclude[1]) {
                    it.@org == 'excludeGroup2'
                    it.@module.isEmpty()
                }
                with(exclude[2]) {
                    it.@org.isEmpty()
                    it.@module == 'excludeModule3'
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

    private QName ns(String name) {
        return new QName("http://my.extra.info/${name}", name)
    }
}
