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
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.util.CollectionUtils
import org.gradle.util.TextUtil
import spock.lang.Specification

class MavenPomFileGeneratorTest extends Specification {
    MavenPomFileGenerator generator = new MavenPomFileGenerator()

    def "writes correct prologue and schema declarations"() {
        expect:
        pomContent.startsWith(TextUtil.toPlatformLineSeparators(
"""<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
"""))
    }

    def "writes empty pom with default values"() {
        expect:
        with (pom) {
            modelVersion == "4.0.0"
            groupId == "unknown"
            artifactId == "empty-project"
            version == "0"
            dependencies.empty
        }
    }

    def "writes configured coordinates"() {
        when:
        generator.groupId = "group-id"
        generator.artifactId = "artifact-id"
        generator.version = "1.0"
        generator.packaging = "pom"

        then:
        with (pom) {
            groupId == "group-id"
            artifactId == "artifact-id"
            version == "1.0"
            packaging == "pom"
        }
    }

    def "encodes coordinates for XML and unicode"() {
        when:
        generator.groupId = 'group-ぴ₦ガき∆ç√∫'
        generator.artifactId = 'artifact-<tag attrib="value"/>-markup'
        generator.version = 'version-&"'

        then:
        with (pom) {
            groupId == 'group-ぴ₦ガき∆ç√∫'
            artifactId == 'artifact-<tag attrib="value"/>-markup'
            version == 'version-&"'
        }
    }

    def "writes regular dependency"() {
        def dependency = Mock(ModuleDependency)
        when:
        generator.addRuntimeDependency(dependency)

        then:
        dependency.artifacts >> new HashSet<DependencyArtifact>()
        dependency.group >> "dep-group"
        dependency.name >> "dep-name"
        dependency.version >> "dep-version"

        and:
        with (pom) {
            dependencies.dependency.size() == 1
            with (dependencies[0].dependency[0]) {
                groupId == "dep-group"
                artifactId == "dep-name"
                version == "dep-version"
                scope == "runtime"
            }
        }
    }

    def "writes project dependency"() {
        def dependency = Mock(ProjectDependency)
        when:
        generator.addRuntimeDependency(dependency)

        then:
        dependency.artifacts >> new HashSet<DependencyArtifact>()
        dependency.group >> "dep-group"
        dependency.version >> "dep-version"
        dependency.dependencyProject >> Stub(Project) {
            getName() >> "project-name"
        }

        and:
        with (pom) {
            dependencies.dependency.size() == 1
            with (dependencies[0].dependency[0]) {
                groupId == "dep-group"
                artifactId == "project-name"
                version == "dep-version"
                scope == "runtime"
            }
        }
    }

    def "writes dependency with artifacts"() {
        def dependency = Mock(ModuleDependency)
        def artifact1 = Mock(DependencyArtifact)
        def artifact2 = Mock(DependencyArtifact)
        
        when:
        generator.addRuntimeDependency(dependency)

        then:
        dependency.artifacts >> CollectionUtils.toSet([artifact1, artifact2])
        dependency.group >> "dep-group"
        dependency.version >> "dep-version"
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
                version == "dep-version"
                type == "type-1"
                classifier == "classifier-1"
                scope == "runtime"
            }
            with (dependencies[0].dependency[1]) {
                groupId == "dep-group"
                artifactId == "artifact-2"
                version == "dep-version"
                type.empty
                classifier.empty
                scope == "runtime"
            }
        }
    }

    private def getPom() {
        String pomContent = getPomContent()
        return new XmlSlurper().parse(new StringReader(pomContent));
    }

    private String getPomContent() {
        def writer = new StringWriter()
        generator.write(writer)
        return writer.toString()
    }
}
