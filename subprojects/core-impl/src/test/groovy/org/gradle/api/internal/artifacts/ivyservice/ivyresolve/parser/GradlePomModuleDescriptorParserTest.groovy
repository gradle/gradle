/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.parser.ParserSettings
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class GradlePomModuleDescriptorParserTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final GradlePomModuleDescriptorParser parser = new GradlePomModuleDescriptorParser()
    final ModuleScopedParserSettings ivySettings = Mock()
    TestFile pomFile

    def "setup"() {
        pomFile = tmpDir.file('foo')
    }

    def "parses simple pom"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <name>Test Artifact One</name>
    <description>The first test artifact</description>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        ivySettings.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
    }

    def "pom with dependency with classifier"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <classifier>classifier-two</classifier>
        </dependency>
    </dependencies>
</project>
"""
        and:
        ivySettings.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDependencyArtifact(descriptor.dependencies.first(), 'artifact-two', 'jar', 'jar', 'classifier-two')
    }

    @Issue("GRADLE-2068")
    def "pom with dependency with empty classifier is treated like dependency without classifier"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-two</version>
            <classifier></classifier>
        </dependency>
    </dependencies>
</project>
"""
        and:
        ivySettings.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
    }

    @Issue("GRADLE-2076")
    def "pom with packaging of type eclipse-plugin creates jar artifact"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>eclipse-plugin</packaging>
</project>
"""
        and:
        ivySettings.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'eclipse-plugin', 'jar')
        descriptor.dependencies.length == 0
    }

    private ModuleDescriptor parsePom() {
        parser.parseDescriptor(ivySettings, pomFile.toURI().toURL(), false)
    }

    private void hasArtifact(ModuleDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        descriptor.allArtifacts.length == 1
        def artifact = descriptor.allArtifacts.first()
        assert artifact.id == artifactId(descriptor.moduleRevisionId, name, type, ext)
        assert artifact.extraAttributes['classifier'] == classifier
    }
    
    private void hasDefaultDependencyArtifact(DependencyDescriptor descriptor) {
        descriptor.allDependencyArtifacts.length == 0
    }

    private void hasDependencyArtifact(DependencyDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        descriptor.allDependencyArtifacts.length == 1
        def artifact = descriptor.allDependencyArtifacts.first()
        assert artifact.name == name
        assert artifact.type == type
        assert artifact.ext == ext
        assert artifact.extraAttributes['classifier'] == classifier
    }

    private ModuleRevisionId moduleId(String group, String name, String version) {
        ModuleRevisionId.newInstance(group, name, version)
    }

    private ArtifactRevisionId artifactId(ModuleRevisionId moduleId, String name, String type, String ext) {
        ArtifactRevisionId.newInstance(moduleId, name, type, ext)
    }
}
