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
    private static final String DEFAULT_COORDINATES =
        """  <modelVersion>4.0.0</modelVersion>
  <groupId>unknown</groupId>
  <artifactId>empty-project</artifactId>
  <version>0</version>"""
    MavenPomFileGenerator generator = new MavenPomFileGenerator()

    def "writes empty pom with default values"() {
        expect:
        checkPomContent """
$DEFAULT_COORDINATES
"""
    }

    def "writes configured coordinates"() {
        when:
        generator.groupId = "group-id"
        generator.artifactId = "artifact-id"
        generator.version = "1.0"
        generator.packaging = "pom"

        then:
        checkPomContent """
  <modelVersion>4.0.0</modelVersion>
  <groupId>group-id</groupId>
  <artifactId>artifact-id</artifactId>
  <version>1.0</version>
  <packaging>pom</packaging>
"""
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
        checkPomContent """
$DEFAULT_COORDINATES
  <dependencies>
    <dependency>
      <groupId>dep-group</groupId>
      <artifactId>dep-name</artifactId>
      <version>dep-version</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
"""
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
        checkPomContent """
$DEFAULT_COORDINATES
  <dependencies>
    <dependency>
      <groupId>dep-group</groupId>
      <artifactId>project-name</artifactId>
      <version>dep-version</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
"""
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
        checkPomContent """
$DEFAULT_COORDINATES
  <dependencies>
    <dependency>
      <groupId>dep-group</groupId>
      <artifactId>artifact-1</artifactId>
      <version>dep-version</version>
      <type>type-1</type>
      <classifier>classifier-1</classifier>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>dep-group</groupId>
      <artifactId>artifact-2</artifactId>
      <version>dep-version</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
"""
    }

    private void checkPomContent(def content) {
        def expected = """<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">$content</project>
"""
        expected = TextUtil.toPlatformLineSeparators(expected)

        assert generatedPom == expected
    }

    private String getGeneratedPom() {
        def writer = new StringWriter()
        generator.write(writer)

        writer.buffer.toString()
    }
}
