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
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class GradlePomModuleDescriptorParserTest extends Specification {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final GradlePomModuleDescriptorParser parser = new GradlePomModuleDescriptorParser()
    final parseContext = Mock(DescriptorParseContext)
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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
        parser.typeName == 'POM'
        parser.toString() == 'gradle pom parser'
    }

    def "uses dependency management section to provide default values for a dependency"() {
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
        </dependency>
    </dependencies>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
                <scope>test</scope>
                <exclusions>
                    <exclusion>
                        <groupId>group-three</groupId>
                        <artifactId>artifact-three</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['test']
        dep.allExcludeRules.length == 1
        dep.allExcludeRules.first().id.moduleId == ModuleId.newInstance('group-three', 'artifact-three')
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom dependency management section to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')
        parseContext.getArtifact(_) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', '1.2')
        dep.moduleConfigurations == ['test']
        hasDefaultDependencyArtifact(dep)
    }

    def "uses properties from parent pom to replace variable placeholders in pom"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <properties>
        <artifact-two.version>v2</artifact-two.version>
        <artifact-three.version>ignore-me</artifact-three.version>
    </properties>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <properties>
        <artifact-three.version>v3</artifact-three.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>\${artifact-two.version}</version>
        </dependency>
        <dependency>
            <groupId>group-three</groupId>
            <artifactId>artifact-three</artifactId>
            <version>\${artifact-three.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')
        parseContext.getArtifact(_) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 2
        def artifactTwo = descriptor.dependencies[0]
        artifactTwo.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'v2')
        hasDefaultDependencyArtifact(artifactTwo)
        def artifactThree = descriptor.dependencies[1]
        artifactThree.dependencyRevisionId == moduleId('group-three', 'artifact-three', 'v3')
        hasDefaultDependencyArtifact(artifactThree)
    }

    def "replaces variable placeholders in parent pom dependency management section"() {
        given:
        def grandParent = tmpDir.file("grand-parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>
</project>
"""

        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>\${project.groupId}</groupId>
                <artifactId>artifact-two</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>\${project.groupId}</groupId>
                <artifactId>artifact-three</artifactId>
                <version>\${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
        <dependency>
            <groupId>group-one</groupId>
            <artifactId>artifact-three</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')
        parseContext.getArtifact({it.moduleRevisionId.name == 'parent' }) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getArtifact({it.moduleRevisionId.name == 'grandparent' }) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 2
        def artifactTwo = descriptor.dependencies[0]
        artifactTwo.dependencyRevisionId == moduleId('group-one', 'artifact-two', 'version-one')
        artifactTwo.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(artifactTwo)
        def artifactThree = descriptor.dependencies[1]
        artifactThree.dependencyRevisionId == moduleId('group-one', 'artifact-three', 'version-one')
        artifactThree.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(artifactThree)
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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'eclipse-plugin', 'jar')
        descriptor.dependencies.length == 0
    }

    def "fails when POM is not well formed XML"() {
        given:
        pomFile << """
<project>
    <modelVersion
</project>
"""

        when:
        parseMetaData()

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse POM ${pomFile.toURI()}"
        e.cause.message.contains('Element type "modelVersion"')
    }

    @Issue("GRADLE-2034")
    def "pom with meta data only"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>pom</packaging>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def metaData = parseMetaData()
        def descriptor = metaData.descriptor

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        descriptor.allArtifacts.length == 0
        descriptor.dependencies.length == 0
        metaData.metaDataOnly
    }

    def "pom with project coordinates defined by custom properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${some.group}</groupId>
    <artifactId>\${some.artifact}</artifactId>
    <version>\${some.version}</version>
    <properties>
        <some.group>group-one</some.group>
        <some.artifact>artifact-one</some.artifact>
        <some.version>version-one</some.version>
    </properties>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 0
    }

    def "pom with dependency coordinates defined by custom properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <properties>
        <some.group>group-two</some.group>
        <some.artifact>artifact-two</some.artifact>
        <some.version>version-two</some.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 1
        descriptor.dependencies.first().dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(descriptor.dependencies.first())
    }

    private ModuleDescriptor parsePom() {
        parseMetaData().descriptor
    }

    private MutableModuleVersionMetaData parseMetaData() {
        parser.parseMetaData(parseContext, pomFile, true)
    }

    private void hasArtifact(ModuleDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        assert descriptor.allArtifacts.length == 1
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
