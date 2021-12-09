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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Issue

import static org.gradle.api.internal.component.ArtifactType.MAVEN_POM

class GradlePomModuleDescriptorParserTest extends AbstractGradlePomModuleDescriptorParserTest {
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

        when:
        def metaData = parseMetaData()

        then:
        metaData instanceof MutableMavenModuleResolveMetadata
        metaData.id == componentId('group-one', 'artifact-one', 'version-one')
        def dependency = single(metaData.dependencies)
        dependency.selector == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(dependency)

        parser.typeName == 'POM'
        parser.toString() == 'gradle pom parser'
    }

    def "converts timestamp version to SNAPSHOT version"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>my-version-20141012.121000-1</version>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'my-version-SNAPSHOT')
    }

    def "Retrieves variables from parent"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <properties>
        <scala.version>2.12.1</scala.version>
        <scala.binary.version>2.12</scala.binary.version>
        <myversion>1.0.0</myversion>
    </properties>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>test</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0</version>
        <relativePath>..</relativePath>
    </parent>

    <artifactId>child_\${scala.binary.version}</artifactId>
    <version>\${myversion}</version>
    <packaging>pom</packaging>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        metadata.moduleVersionId.group == 'test'
        metadata.moduleVersionId.name == 'child_2.12'
        metadata.moduleVersionId.version == '1.0.0'

    }

    def "merges dependencies declared in pom with those declared in parent"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.2</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-three</artifactId>
            <version>1.2</version>
        </dependency>
    </dependencies>
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
            <artifactId>artifact-one</artifactId>
            <version>11</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-three</artifactId>
            <version>11</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        metadata.dependencies.size() == 3

        def dep1 = metadata.dependencies[0]
        dep1.selector == moduleId('group-two', 'artifact-one', '11')
        dep1.scope == MavenScope.Compile

        def dep2 = metadata.dependencies[1]
        dep2.selector == moduleId('group-two', 'artifact-three', '11')
        dep2.scope == MavenScope.Compile

        def inheritedDep = metadata.dependencies[2]
        inheritedDep.selector == moduleId('group-two', 'artifact-two', '1.2')
        inheritedDep.scope == MavenScope.Compile
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

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Test
        def excludeRule = single(dep.allExcludes)
        excludeRule.moduleId == DefaultModuleIdentifier.newId('group-three', 'artifact-three')
        hasDefaultDependencyArtifact(dep)
    }

    def "in case of conflicting entries in the dependency management section, the last seen entry wins"() {
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
            <scope>runtime</scope>
        </dependency>
    </dependencies>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.1</version>
                <scope>compile</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 1
        def depCompile = dependencies[0]
        depCompile.selector == moduleId('group-two', 'artifact-two', '1.1')
        depCompile.scope == MavenScope.Runtime //scope is defined in the dependency declaration and is not replaced
    }

    def "if two dependencyManagement entries for the same dependency are combined, the closest wins a conflict"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
                $scopeInParent
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
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                $versionInChild
                $scopeInChild
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 1
        def depCompile = dependencies[0]
        depCompile.selector == moduleId('group-two', 'artifact-two', selectedVersion)
        depCompile.scope == selectedScope

        where:
        scopeInParent            | scopeInChild          | versionInChild           | selectedVersion | selectedScope
        ""                       | "<scope>test</scope>" | "<version>1.1</version>" | "1.1"           | MavenScope.Test
        "<scope>compile</scope>" | "<scope>test</scope>" | "<version>1.1</version>" | "1.1"           | MavenScope.Test
        "<scope>test</scope>"    | ""                    | "<version>1.1</version>" | "1.1"           | MavenScope.Compile
        ""                       | "<scope>test</scope>" | ""                       | ""              | MavenScope.Test
        "<scope>compile</scope>" | "<scope>test</scope>" | ""                       | ""              | MavenScope.Test
        "<scope>test</scope>"    | ""                    | ""                       | ""              | MavenScope.Compile
    }

    def "uses empty version if parent pom dependency management section does not provide default values for dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-three</groupId>
                <artifactId>artifact-three</artifactId>
                <version>1.2</version>
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
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '')
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
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Test
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom dependency management section with multiple versions of same dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
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
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.3</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.1</version>
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
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.1')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses imported child pom over parent pom dependency management section"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
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
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.5</version>
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

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>different-group</groupId>
                <artifactId>imported</artifactId>
                <version>different-version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.5')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses importing pom dependency management over imported pom definition with same group ID and artifact ID "() {
        given:

        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.5</version>
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

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>different-group</groupId>
                <artifactId>imported</artifactId>
                <version>different-version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses child dependency over parent dependency with same group ID and artifact ID"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.5</version>
        </dependency>
    </dependencies>
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
            <version>1.2</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom dependency with multiple versions of same dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.3</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.5</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.4</version>
        </dependency>
    </dependencies>
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
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.4')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom over grand parent pom dependency management section"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.5</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.2</version>
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
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent pom properties for parent pom dependency management section"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <properties>
        <grandparent.groupid>group-two</grandparent.groupid>
        <grandparent.artifactid>artifact-two</grandparent.artifactid>
    </properties>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
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
                <groupId>\${grandparent.groupid}</groupId>
                <artifactId>\${grandparent.artifactid}</artifactId>
                <version>1.2</version>
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
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "parent pom properties are evaluated lazily"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <properties>
        <artifacttwo.version>2</artifacttwo.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-one</groupId>
                <artifactId>artifacttwo</artifactId>
                <version>\${artifacttwo.version}</version>
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

    <properties>
        <artifacttwo.version>3</artifacttwo.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>group-one</groupId>
            <artifactId>artifacttwo</artifactId>
        </dependency>
    </dependencies>

</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-one', 'artifacttwo', '3')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "grand parent pom properties are evaluated lazily"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <properties>
        <artifacttwo.version>2</artifacttwo.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-one</groupId>
                <artifactId>artifacttwo</artifactId>
                <version>\${artifacttwo.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

</project>
"""
        def parent = tmpDir.file("parent.xml") << """
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
        <artifacttwo.version>3</artifacttwo.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>group-one</groupId>
            <artifactId>artifacttwo</artifactId>
        </dependency>
    </dependencies>

</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)
        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-one', 'artifacttwo', '3')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom properties over grand parent pom properties for dependency management if overridden"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <properties>
        <my.groupid>group-three</my.groupid>
        <my.artifactid>artifact-three</my.artifactid>
    </properties>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
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

    <properties>
        <my.groupid>group-two</my.groupid>
        <my.artifactid>artifact-two</my.artifactid>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>\${my.groupid}</groupId>
                <artifactId>\${my.artifactid}</artifactId>
                <version>1.2</version>
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
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)
        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
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
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        metadata.dependencies.size() == 2
        def artifactTwo = metadata.dependencies[0]
        artifactTwo.selector == moduleId('group-two', 'artifact-two', 'v2')
        hasDefaultDependencyArtifact(artifactTwo)
        def artifactThree = metadata.dependencies[1]
        artifactThree.selector == moduleId('group-three', 'artifact-three', 'v3')
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
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)
        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        def artifactTwo = dependencies[0]
        artifactTwo.selector == moduleId('group-one', 'artifact-two', 'version-one')
        hasDefaultDependencyArtifact(artifactTwo)
        def artifactThree = dependencies[1]
        artifactThree.selector == moduleId('group-one', 'artifact-three', 'version-one')
        hasDefaultDependencyArtifact(artifactThree)
    }

    @Issue("GRADLE-2918")
    def "uses imported BOM in grand parent dependency management section"() {
        given:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <properties>
        <myversion>some-version</myversion>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-one</groupId>
                <artifactId>artifact-two</artifactId>
                <version>\${myversion}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        def grandParent = tmpDir.file("grand-parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>different-group</groupId>
                <artifactId>imported</artifactId>
                <version>different-version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
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
            <groupId>group-one</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-one', 'artifact-two', 'some-version')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDependencyArtifact(dep, 'artifact-two', 'jar', 'jar', 'classifier-two')
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.empty
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
        e.message == "Could not parse POM ${pomFile}"
        e.cause.message.contains('"modelVersion"')
    }


    def "pom with no dependencies "() {
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.empty
    }

    def "pom with packaging 'pom'"() {
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

        when:
        def metaData = parseMetaData()

        then:
        metaData.packaging == 'pom'
    }

    @Issue("GRADLE-3074")
    def "pom with packaging defined by custom property"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>\${package.type}</packaging>
    <properties>
        <package.type>war</package.type>
    </properties>
</project>
"""

        when:
        def metaData = parseMetaData()

        then:
        metaData.packaging == 'war'
    }

    @Issue("GRADLE-3074")
    def "pom with packaging defined by custom property in parent pom"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <properties>
        <package.type>war</package.type>
    </properties>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>\${package.type}</packaging>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        def metaData = parseMetaData()

        then:
        metaData.packaging == 'war'
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.empty
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

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom over grand parent pom dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.5</version>
        </dependency>
    </dependencies>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>different-group</groupId>
        <artifactId>grandparent</artifactId>
        <version>different-version</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <version>1.2</version>
        </dependency>
    </dependencies>
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
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent pom properties for parent pom dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <properties>
        <grandparent.groupid>group-two</grandparent.groupid>
        <grandparent.artifactid>artifact-two</grandparent.artifactid>
    </properties>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
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

    <dependencies>
        <dependency>
            <groupId>\${grandparent.groupid}</groupId>
            <artifactId>\${grandparent.artifactid}</artifactId>
            <version>1.2</version>
        </dependency>
    </dependencies>
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
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom properties over grand parent pom properties for dependency if overridden"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <properties>
        <my.groupid>group-three</my.groupid>
        <my.artifactid>artifact-three</my.artifactid>
    </properties>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
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

    <properties>
        <my.groupid>group-two</my.groupid>
        <my.artifactid>artifact-two</my.artifactid>
    </properties>

    <dependencies>
        <dependency>
            <groupId>\${my.groupid}</groupId>
            <artifactId>\${my.artifactid}</artifactId>
            <version>1.2</version>
        </dependency>
    </dependencies>
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
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "defines relocation but doesn't include any explicit dependencies or artifacts"() {
        given:
        def relocated = tmpDir.file("relocated.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-relocated</groupId>
    <artifactId>relocated</artifactId>
    <version>version-one</version>
</project>
"""

        def parent = tmpDir.file("parent.xml") << """
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
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-three</groupId>
            <artifactId>artifact-three</artifactId>
            <version>1.3</version>
        </dependency>
    </dependencies>
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

    <distributionManagement>
        <relocation>
            <groupId>group-relocated</groupId>
            <artifactId>relocated</artifactId>
        </relocation>
    </distributionManagement>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.selector.module == 'relocated' }, _, MAVEN_POM) >> asResource(relocated)


        when:
        parsePom()

        then:
        MavenDependencyDescriptor dep = single(metadata.dependencies) as MavenDependencyDescriptor
        dep.selector == moduleId('group-relocated', 'relocated', 'version-one')
        dep.scope == MavenScope.Compile
        dep.dependencyArtifact == null
    }

    @Issue("GRADLE-2931")
    def "handles dependencies with same group ID and artifact ID but different type and classifier"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.1</version>
            </dependency>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <type>test-jar</type>
                <version>1.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <artifactId>artifact-one</artifactId>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        def depCompile = dependencies[0]
        depCompile.selector == moduleId('group-two', 'artifact-two', '1.1')
        depCompile.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(depCompile)
        def depTest = dependencies[1]
        depTest.selector == moduleId('group-two', 'artifact-two', '1.2')
        depTest.scope == MavenScope.Test
        hasDependencyArtifact(depTest, 'artifact-two', 'test-jar', 'jar', 'tests')
    }

    def "parent dependency management applies does not apply to dependency with different type"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.1</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>parent</artifactId>
        <version>version-one</version>
    </parent>

    <artifactId>artifact-one</artifactId>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        def dep1 = dependencies.find { it.dependencyArtifact == null }
        dep1.selector == moduleId('group-two', 'artifact-two', '1.1')
        def dep2 = dependencies.find { it.dependencyArtifact != null }
        dep2.selector == moduleId('group-two', 'artifact-two', '')
        dep2.dependencyArtifact == new DefaultIvyArtifactName('artifact-two', 'test-jar', 'jar', 'tests')
    }

    @Issue("GRADLE-2931")
    def "picks version of last dependency defined by artifact ID, group ID, type and classifier"() {
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
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-two</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-three</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-four</version>
        </dependency>
    </dependencies>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-four')
        dep.scope == MavenScope.Compile
        hasDependencyArtifact(dep, 'artifact-two', 'jar', 'jar', 'myjar')
    }

    @Issue("GRADLE-2931")
    def "can declare multiple dependencies with same artifact ID and group ID but different type and classifier"() {
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
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>version-three</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <version>version-four</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <classifier>test</classifier>
            <version>version-five</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
            <type>ejb-client</type>
            <version>version-six</version>
        </dependency>
    </dependencies>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.size() == 5
        def defDep = metadata.dependencies[0]
        defDep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        defDep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(defDep)
        def depJar = metadata.dependencies[1]
        depJar.selector == moduleId('group-two', 'artifact-two', 'version-three')
        depJar.scope == MavenScope.Compile
        hasDependencyArtifact(depJar, 'artifact-two', 'jar', 'jar', 'myjar')
        def depTestJar = metadata.dependencies[2]
        depTestJar.selector == moduleId('group-two', 'artifact-two', 'version-four')
        depTestJar.scope == MavenScope.Compile
        hasDependencyArtifact(depTestJar, 'artifact-two', 'test-jar', 'jar', 'tests')
        def depTestJarWithClassifier = metadata.dependencies[3]
        depTestJarWithClassifier.selector == moduleId('group-two', 'artifact-two', 'version-five')
        depTestJarWithClassifier.scope == MavenScope.Compile
        hasDependencyArtifact(depTestJarWithClassifier, 'artifact-two', 'test-jar', 'jar', 'test')
        def depEjbClient = metadata.dependencies[4]
        depEjbClient.selector == moduleId('group-two', 'artifact-two', 'version-six')
        depEjbClient.scope == MavenScope.Compile
        hasDependencyArtifact(depEjbClient, 'artifact-two', 'ejb-client', 'jar', 'client')
    }

    @Issue("GRADLE-2371")
    def "can declare multiple dependencies with same artifact ID and group ID but different type and classifier and scope"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>group-one</groupId>
            <artifactId>artifact-two</artifactId>
            <version>version-one</version>
        </dependency>
        <dependency>
            <groupId>group-one</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <version>version-one</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
        <dependency>
            <groupId>\${project.groupId}</groupId>
            <artifactId>artifact-two</artifactId>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        def defDep = dependencies[0]
        defDep.selector == moduleId('group-one', 'artifact-two', 'version-one')
        defDep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(defDep)
        def depJar = dependencies[1]
        depJar.selector == moduleId('group-one', 'artifact-two', 'version-one')
        depJar.scope == MavenScope.Test
        hasDependencyArtifact(depJar, 'artifact-two', 'test-jar', 'jar', 'tests')
    }

    @Issue("GRADLE-2938")
    def "uses default dependency type if only the dependency management or dependency element declares it"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
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
                <version>version-two</version>
                <type>jar</type>
            </dependency>
            <dependency>
                <groupId>group-three</groupId>
                <artifactId>artifact-three</artifactId>
                <version>version-three</version>
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
        <dependency>
            <groupId>group-three</groupId>
            <artifactId>artifact-three</artifactId>
            <type>jar</type>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        def depGroupTwo = dependencies[0]
        depGroupTwo.selector == moduleId('group-two', 'artifact-two', 'version-two')
        depGroupTwo.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(depGroupTwo)
        def depGroupThree = dependencies[1]
        depGroupThree.selector == moduleId('group-three', 'artifact-three', 'version-three')
        depGroupThree.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(depGroupThree)
    }

    @Issue("GRADLE-2982")
    def "use imported pom even though dependency is declared multiple times with different scopes"() {
        given:
        def imported = tmpDir.file("imported.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>different-group</groupId>
    <artifactId>imported</artifactId>
    <version>different-version</version>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-two</groupId>
                <artifactId>artifact-two</artifactId>
                <version>1.5</version>
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

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>different-group</groupId>
                <artifactId>imported</artifactId>
                <version>different-version</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>different-group</groupId>
                <artifactId>imported</artifactId>
                <version>different-version</version>
                <type>pom</type>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.selector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.5')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "converts #inputVersion version to #outputVersion"() {
        given:
        pomFile << """
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
                <version>$inputVersion</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>group-three</groupId>
            <artifactId>artifact-three</artifactId>
            <type>jar</type>
            <classifier>myjar</classifier>
            <version>$inputVersion</version>
        </dependency>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>
</project>
"""
        when:
        parsePom()

        then:
        def dependencies = dependenciesOnly(metadata.dependencies)
        dependencies.size() == 2
        dependencies.every {it.selector.version == outputVersion}

        where:
        inputVersion | outputVersion
        "RELEASE"    | "latest.release"
        "LATEST"     | "latest.integration"
    }


    def "handles dependency with type #type"() {
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
            <version>1.2</version>
            <type>${type}</type>
        </dependency>
    </dependencies>
</project>
"""

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDependencyArtifact(dep, 'artifact-two', type, extension, classifier)

        where:
        type          | extension     | classifier
        'test-jar'    | 'jar'         | 'tests'
        'ejb'         | 'jar'         | null
        'ejb-client'  | 'jar'         | 'client'
        'bundle'      | 'jar'         | null
        'custom-type' | 'custom-type' | null
    }

    @Issue("GRADLE-3299")
    def "correctly resolve references to parent GAV properties"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
</project>
"""

        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${project.parent.groupId}</groupId>
    <artifactId>\${project.parent.artifactId}-ext</artifactId>
    <version>\${project.parent.version}</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>artifact-one</artifactId>
        <version>version-one</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>\${parent.groupId}</groupId>
            <artifactId>\${parent.artifactId}-xxx</artifactId>
            <version>\${parent.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact(_, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one-ext', 'version-one')

        def depGroupOne = single(metadata.dependencies)
        depGroupOne.selector == moduleId('group-one', 'artifact-one-xxx', 'version-one')
        hasDefaultDependencyArtifact(depGroupOne)
    }

    @Issue("gradle/gradle#1084")
    def "ignores parent if it has the same GAV as resolved pom"() {
        // Maven forbids to _create_ a project with a self referencing parent POM but parses a dependency built that way
        given:
        def pomWithParent = """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <parent>
        <groupId>group-one</groupId>
        <artifactId>artifact-one</artifactId>
        <version>version-one</version>
    </parent>
</project>
"""
        pomFile << pomWithParent

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')

    }
}
