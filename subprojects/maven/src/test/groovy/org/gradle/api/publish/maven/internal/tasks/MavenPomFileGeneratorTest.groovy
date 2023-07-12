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

package org.gradle.api.publish.maven.internal.tasks

import com.google.common.collect.ImmutableList
import groovy.xml.XmlSlurper
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPom
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomDependencies
import org.gradle.api.publish.maven.internal.publication.MavenPomDependencies
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class MavenPomFileGeneratorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def rangeMapper = Stub(VersionRangeMapper)
    def strategy = Stub(VersionMappingStrategyInternal) {
        findStrategyForVariant(_) >> Stub(VariantVersionMappingStrategyInternal) {
            maybeResolveVersion(_, _, _) >> null
        }
    }
    def generator = new MavenPomFileGenerator(rangeMapper, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY)
    def pom = newPom()

    def "writes correct prologue and schema declarations"() {
        expect:
        writePomFile().text.startsWith(TextUtil.toPlatformLineSeparators(
"""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
"""))
    }

    def "writes Gradle metadata marker"() {
        given:
        pom.getWriteGradleMetadataMarker().set(markerPresent)

        when:
        def pomFile = writePomFile()

        then:
        pomFile.text.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) == markerPresent

        where:
        markerPresent << [true, false]
    }

    def "writes configured coordinates"() {
        expect:
        with (xml) {
            groupId == "group-id"
            artifactId == "artifact-id"
            version == "1.0"
            packaging.empty
        }
    }

    def "writes metadata from configuration"() {
        given:
        with(pom) {
            setPackaging("pom")
            name.set("my name")
            description.set("my description")
            url.set("http://example.org")
            inceptionYear.set("2018")
            licenses {
                license {
                    name.set("GPL")
                    url.set("http://www.gnu.org/licenses/gpl.html")
                }
            }
            organization {
                name.set("Some Org")
            }
            developers {
                developer {
                    name.set("Alice")
                }
            }
            contributors {
                contributor {
                    name.set("Bob")
                }
            }
            scm {
                connection.set("http://cvs.example.org")
            }
            issueManagement {
                system.set("Bugzilla")
            }
            ciManagement {
                system.set("Anthill")
            }
            distributionManagement {
                downloadUrl.set("https://example.org/download/")
                relocation {
                    groupId.set("org.example.new")
                }
            }
            mailingLists {
                mailingList {
                    name.set("Users")
                }
            }
            properties.put("spring-boot.version", "2.1.2.RELEASE")
            properties.put("hibernate.version", "5.4.1.Final")
        }

        expect:
        with (xml) {
            packaging == "pom"
            name == "my name"
            description == "my description"
            inceptionYear == "2018"
            url == "http://example.org"
            licenses.license.name == "GPL"
            licenses.license.url == "http://www.gnu.org/licenses/gpl.html"
            organization.name == "Some Org"
            developers.developer.name == "Alice"
            contributors.contributor.name == "Bob"
            scm.connection == "http://cvs.example.org"
            issueManagement.system == "Bugzilla"
            ciManagement.system == "Anthill"
            distributionManagement.relocation.groupId == "org.example.new"
            mailingLists.mailingList.name == "Users"
            properties["spring-boot.version"] == "2.1.2.RELEASE"
            properties["hibernate.version"] == "5.4.1.Final"
        }
    }

    def "encodes coordinates for XML and unicode"() {
        given:
        pom.coordinates.groupId.set('group-ぴ₦ガき∆ç√∫')
        pom.coordinates.artifactId.set('artifact-<tag attrib="value"/>-markup')
        pom.coordinates.version.set('version-&"')

        expect:
        with (xml) {
            groupId == 'group-ぴ₦ガき∆ç√∫'
            artifactId == 'artifact-<tag attrib="value"/>-markup'
            version == 'version-&"'
        }

        where:
        marker << [false, true]
    }

    def "writes regular dependency"() {
        def dependency = new DefaultMavenDependency("dep-group", "dep-name", "dep-version")
        pom.getDependencies().set(runtimeDependencies(dependency))

        when:
        rangeMapper.map("dep-version") >> "maven-dep-version"

        then:
        with (xml) {
            dependencies.dependency.size() == 1
            with (dependencies[0].dependency[0]) {
                groupId == "dep-group"
                artifactId == "dep-name"
                version == "maven-dep-version"
                scope == "runtime"
            }
        }
    }

    def "writes regular dependency without exclusions"() {
        given:
        def dependency = new DefaultMavenDependency("dep-group", "dep-name", "dep-version")
        pom.getDependencies().set(runtimeDependencies(dependency))

        expect:
        with (xml) {
            dependencies.dependency.exclusions.size() == 0
        }
    }

    def "writes dependency with excludes"() {
        given:
        def exclude1 = Mock(ExcludeRule)
        def exclude2 = Mock(ExcludeRule)
        def exclude3 = Mock(ExcludeRule)
        def dependency = new DefaultMavenDependency("dep-group", "dep-name", "dep-version", [], [exclude1, exclude2, exclude3])

        pom.getDependencies().set(runtimeDependencies(dependency))

        when:
        exclude1.group >> "excl-1-group"
        exclude1.module >> "excl-1-module"
        exclude2.group >> "excl-2-group"
        exclude2.module >> null
        exclude3.group >> null
        exclude3.module >> "excl-3-module"

        then:
        with (xml) {
            dependencies.dependency.exclusions.exclusion.size() == 3
            with (dependencies[0].dependency[0].exclusions[0].exclusion[0]) {
                groupId == "excl-1-group"
                artifactId == "excl-1-module"
            }
            with (dependencies[0].dependency[0].exclusions[0].exclusion[1]) {
                groupId == "excl-2-group"
                artifactId == "*"
            }
            with (dependencies[0].dependency[0].exclusions[0].exclusion[2]) {
                groupId == "*"
                artifactId == "excl-3-module"
            }
        }
    }

    def "writes dependency with artifacts"() {
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)
        def dependency = new DefaultMavenDependency("dep-group", "dep-name", "dep-version", [artifact1, artifact2], [])
        pom.getDependencies().set(runtimeDependencies(dependency))

        when:
        rangeMapper.map("dep-version") >> "maven-dep-version"
        artifact1.name >> "artifact-1"
        artifact1.type >> "type-1"
        artifact1.classifier >> "classifier-1"
        artifact2.name >> "artifact-2"
        artifact2.type >> null
        artifact2.classifier >> null

        then:
        with (xml) {
            dependencies.dependency.size() == 2
            with (dependencies[0].dependency[0]) {
                groupId == "dep-group"
                artifactId == "artifact-1"
                version == "maven-dep-version"
                type == "type-1"
                classifier == "classifier-1"
                scope == "runtime"
            }
            with (dependencies[0].dependency[1]) {
                groupId == "dep-group"
                artifactId == "artifact-2"
                version == "maven-dep-version"
                type.empty
                classifier.empty
                scope == "runtime"
            }
        }
    }

    def "applies withXml actions"() {
        when:
        pom.withXml {
            asNode().groupId[0].value = "new-group"
        }
        pom.withXml {
            asNode().appendNode("description", "custom-description-ぴ₦ガき∆ç√∫")
        }

        then:
        with (xml) {
            groupId == "new-group"
            description == "custom-description-ぴ₦ガき∆ç√∫"
        }
    }

    private MavenPomDependencies runtimeDependencies(MavenDependencyInternal dependency) {
        return new DefaultMavenPomDependencies(
            ImmutableList.of(dependency), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of()
        )
    }

    private MavenPomInternal newPom() {
        MavenPomInternal pom = TestUtil.objectFactory().newInstance(
            DefaultMavenPom.class,
            TestUtil.objectFactory(),
            strategy
        )

        pom.coordinates.groupId.set("group-id")
        pom.coordinates.artifactId.set("artifact-id")
        pom.coordinates.version.set("1.0")

        pom.dependencies.set(DefaultMavenPomDependencies.EMPTY)
        pom.getWriteGradleMetadataMarker().set(true)

        return pom
    }

    private def getXml() {
        return new XmlSlurper().parse(writePomFile());
    }

    private TestFile writePomFile() {
        def pomFile = testDirectoryProvider.testDirectory.file("pom.xml")
        generator.generateSpec(pom).writeTo(pomFile)
        return pomFile
    }
}
