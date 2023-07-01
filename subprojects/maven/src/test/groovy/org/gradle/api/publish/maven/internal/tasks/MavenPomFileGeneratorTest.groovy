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

import groovy.xml.XmlSlurper
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.provider.Property
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomDeveloper
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomDistributionManagement
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomLicense
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomMailingList
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomOrganization
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomProjectManagement
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPomScm
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.publication.ReadableMavenProjectIdentity
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class MavenPomFileGeneratorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def projectIdentity = new ReadableMavenProjectIdentity("group-id", "artifact-id", "1.0")
    def rangeMapper = Stub(VersionRangeMapper)
    def strategy = Stub(VersionMappingStrategyInternal) {
        findStrategyForVariant(_) >> Stub(VariantVersionMappingStrategyInternal) {
            maybeResolveVersion(_, _, _) >> null
        }
    }
    def generator = new MavenPomFileGenerator(projectIdentity, rangeMapper, strategy, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, false)
    def instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def objectFactory = TestUtil.objectFactory()

    def "writes correct prologue and schema declarations"() {
        expect:
        pomFile.text.startsWith(TextUtil.toPlatformLineSeparators(
"""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
"""))
    }

    def "writes Gradle metadata marker"() {
        generator = new MavenPomFileGenerator(projectIdentity, rangeMapper, strategy, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, markerPresent)

        expect:
        pomFile.text.contains(MetaDataParser.GRADLE_6_METADATA_MARKER) == markerPresent

        where:
        markerPresent << [true, false]
    }

    def "writes configured coordinates"() {
        expect:
        with (pom) {
            groupId == "group-id"
            artifactId == "artifact-id"
            version == "1.0"
            packaging.empty
        }
    }

    def "does not require metadata to be configured"() {
        given:
        def mavenPom = Mock(MavenPomInternal) {
            getPackaging() >> "pom"
            getName() >> objectFactory.property(String)
            getDescription() >> objectFactory.property(String)
            getUrl() >> objectFactory.property(String)
            getInceptionYear() >> objectFactory.property(String)
            getLicenses() >> []
            getDevelopers() >> []
            getContributors() >> []
            getMailingLists() >> []
            getProperties() >> objectFactory.mapProperty(String, String)
        }

        when:
        generator.configureFrom(mavenPom)

        then:
        with (pom) {
            packaging == "pom"
        }
    }

    def "writes metadata from configuration"() {
        given:
        def mavenPom = Mock(MavenPomInternal) {
            getPackaging() >> "pom"
            getName() >> propertyWithValue("my name")
            getDescription() >> propertyWithValue("my description")
            getUrl() >> propertyWithValue("http://example.org")
            getInceptionYear() >> propertyWithValue("2018")
            getLicenses() >> [new DefaultMavenPomLicense(objectFactory) {{
                getName().set("GPL")
                getUrl().set("http://www.gnu.org/licenses/gpl.html")
            }}]
            getOrganization() >> new DefaultMavenPomOrganization(objectFactory) {{
                getName().set("Some Org")
            }}
            getDevelopers() >> [new DefaultMavenPomDeveloper(objectFactory) {{
                getName().set("Alice")
            }}]
            getContributors() >> [new DefaultMavenPomDeveloper(objectFactory) {{
                getName().set("Bob")
            }}]
            getScm() >> new DefaultMavenPomScm(objectFactory) {{
                getConnection().set("http://cvs.example.org")
            }}
            getIssueManagement() >> new DefaultMavenPomProjectManagement(objectFactory) {{
                getSystem().set("Bugzilla")
            }}
            getCiManagement() >> new DefaultMavenPomProjectManagement(objectFactory) {{
                getSystem().set("Anthill")
            }}
            getDistributionManagement() >> new DefaultMavenPomDistributionManagement(instantiator, objectFactory) {{
                getDownloadUrl().set("https://example.org/download/")
                relocation { r ->
                    r.getGroupId().set("org.example.new")
                }
            }}
            getMailingLists() >> [new DefaultMavenPomMailingList(objectFactory) {{
                getName().set("Users")
            }}]
            getProperties() >> TestUtil.objectFactory().mapProperty(String, String).with {
                put("spring-boot.version", "2.1.2.RELEASE")
                put("hibernate.version", "5.4.1.Final")
                return it
            }
        }

        when:
        generator.configureFrom(mavenPom)

        then:
        with (pom) {
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

    private <T> Property<T> propertyWithValue(T value) {
        def property = objectFactory.property(T)
        property.set(value)
        return property
    }

    def "encodes coordinates for XML and unicode"() {
        when:
        def groupId = 'group-ぴ₦ガき∆ç√∫'
        def artifactId = 'artifact-<tag attrib="value"/>-markup'
        def version = 'version-&"'
        generator = new MavenPomFileGenerator(new ReadableMavenProjectIdentity(groupId, artifactId, version), Stub(VersionRangeMapper), Stub(VersionMappingStrategyInternal), ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, marker)

        then:
        with (pom) {
            groupId == 'group-ぴ₦ガき∆ç√∫'
            artifactId == 'artifact-<tag attrib="value"/>-markup'
            version == 'version-&"'
        }

        where:
        marker << [false, true]
    }

    def "writes regular dependency"() {
        def dependency = Mock(MavenDependencyInternal)
        when:
        generator.addDependency(dependency, "runtime", false)

        then:
        dependency.artifacts >> new HashSet<DependencyArtifact>()
        dependency.groupId >> "dep-group"
        dependency.artifactId >> "dep-name"
        dependency.version >> "dep-version"
        dependency.excludeRules >> []
        rangeMapper.map("dep-version") >> "maven-dep-version"

        and:
        with (pom) {
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
        def dependency = Mock(MavenDependencyInternal)
        when:
        generator.addDependency(dependency, "runtime", false)

        then:
        dependency.artifacts >> new HashSet<DependencyArtifact>()
        dependency.groupId >> "dep-group"
        dependency.artifactId >> "dep-name"
        dependency.version >> "dep-version"
        dependency.excludeRules >> []

        and:
        with (pom) {
            dependencies.dependency.exclusions.size() == 0
        }
    }

    def "writes dependency with excludes"() {
        given:
        def dependency = Mock(MavenDependencyInternal)
        def exclude1 = Mock(ExcludeRule)
        def exclude2 = Mock(ExcludeRule)
        def exclude3 = Mock(ExcludeRule)

        when:
        generator.addDependency(dependency, "runtime", false)

        then:
        dependency.artifacts >> new HashSet<DependencyArtifact>()
        dependency.groupId >> "dep-group"
        dependency.artifactId >> "dep-name"
        dependency.version >> "dep-version"
        dependency.excludeRules >> CollectionUtils.toSet([exclude1, exclude2, exclude3])
        exclude1.group >> "excl-1-group"
        exclude1.module >> "excl-1-module"
        exclude2.group >> "excl-2-group"
        exclude2.module >> null
        exclude3.group >> null
        exclude3.module >> "excl-3-module"

        and:
        with (pom) {
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
        def dependency = Mock(MavenDependencyInternal)
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)

        when:
        generator.addDependency(dependency, "runtime", false)

        then:
        dependency.artifacts >> CollectionUtils.toSet([artifact1, artifact2])
        dependency.groupId >> "dep-group"
        dependency.version >> "dep-version"
        dependency.excludeRules >> []
        rangeMapper.map("dep-version") >> "maven-dep-version"
        artifact1.name >> "artifact-1"
        artifact1.type >> "type-1"
        artifact1.classifier >> "classifier-1"
        artifact2.name >> "artifact-2"
        artifact2.type >> null
        artifact2.classifier >> null

        and:
        with (pom) {
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
        generator.withXml(new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().groupId[0].value = "new-group"
            }
        })
        generator.withXml(new Action<XmlProvider>() {
            void execute(XmlProvider t) {
                t.asNode().appendNode("description", "custom-description-ぴ₦ガき∆ç√∫")
            }
        })

        then:
        with (pom) {
            groupId == "new-group"
            description == "custom-description-ぴ₦ガき∆ç√∫"
        }
    }

    private def getPom() {
        return new XmlSlurper().parse(pomFile);
    }

    private TestFile getPomFile() {
        def pomFile = testDirectoryProvider.testDirectory.file("pom.xml")
        generator.writeTo(pomFile)
        return pomFile
    }
}
