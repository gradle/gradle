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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.descriptor.MavenScope
import spock.lang.Issue

import static org.gradle.api.internal.component.ArtifactType.MAVEN_POM

class GradlePomModuleDescriptorParserProfileTest extends AbstractGradlePomModuleDescriptorParserTest {

    def "pom with project coordinates defined by active profile properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${some.group}</groupId>
    <artifactId>\${some.artifact}</artifactId>
    <version>\${some.version}</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-one</some.group>
                <some.artifact>artifact-one</some.artifact>
                <some.version>version-one</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.empty
    }

    def "pom with dependency coordinates defined by active profile properties"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dependency = single(metadata.dependencies)
        dependency.selector == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(dependency)
    }

    def "uses parent properties from active profile to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
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
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
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
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent properties from active profile to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
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
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section in active profile to provide default values for a dependency"() {
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

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Test
        def exclude = single(dep.allExcludes)
        exclude.moduleId == moduleIdentifierFactory.module('group-three', 'artifact-three')
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent pom dependency management section in active profile to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent pom dependency management section in active profile to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in active profile to define defaults for main POM body dependency"() {
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

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def deps = metadata.dependencies
        deps.size() == 2
        def dep = metadata.dependencies[0]
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "pom with dependency defined by active profile"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent dependency from active profile"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent dependency from active profile"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent dependency over grand parent dependency with same groupId and artifactId from active profile"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.3</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.3')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in active profile to define defaults for profile dependency"() {
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

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
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
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "pom with project coordinates defined by profile activated by absence of system property"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>\${some.group}</groupId>
    <artifactId>\${some.artifact}</artifactId>
    <version>\${some.version}</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <some.group>group-one</some.group>
                <some.artifact>artifact-one</some.artifact>
                <some.version>version-one</some.version>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        metadata.id == componentId('group-one', 'artifact-one', 'version-one')
        metadata.dependencies.empty
    }

    def "pom with dependency coordinates defined by profile activated by absence of system property"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <dependencies>
        <dependency>
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
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

    def "uses parent properties from activated by absence of system property to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
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
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
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
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent properties from profile activated by absence of system property to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <properties>
                <some.group>group-two</some.group>
                <some.artifact>artifact-two</some.artifact>
                <some.version>version-two</some.version>
            </properties>
        </profile>
    </profiles>
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
            <groupId>\${some.group}</groupId>
            <artifactId>\${some.artifact}</artifactId>
            <version>\${some.version}</version>
        </dependency>
    </dependencies>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section in profile activated by absence of system property to provide default values for a dependency"() {
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

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
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
        </profile>
    </profiles>
</project>
"""

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Test
        hasDefaultDependencyArtifact(dep)
        def excludeRule = single(dep.allExcludes)
        excludeRule.moduleId == DefaultModuleIdentifier.newId('group-three', 'artifact-three')
    }

    def "uses parent pom dependency management section in profile activated by absence of system property to provide default values for a dependency"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent pom dependency management section in profile activated by absence of system property to provide default values for a dependency"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>group-two</groupId>
                        <artifactId>artifact-two</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in profile activated by absence of system property to define defaults for main POM body dependency"() {
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

    <dependencies>
        <dependency>
            <groupId>group-two</groupId>
            <artifactId>artifact-two</artifactId>
        </dependency>
    </dependencies>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
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
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "pom with dependency defined in profile activated by absence of system property"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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

    def "uses parent dependency from profile activated by absence of system property"() {
        given:
        def parent = tmpDir.file("parent.xlm") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>version-two</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        dep.selector == moduleId('group-two', 'artifact-two', 'version-two')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses grand parent dependency from profile activated by absence of system property"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses parent dependency over grand parent dependency with same groupId and artifactId from profile activated by absence of system property"() {
        given:
        def grandParent = tmpDir.file("grandparent.xml") << """
<project>
    <groupId>different-group</groupId>
    <artifactId>grandparent</artifactId>
    <version>different-version</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.2</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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

    <profiles>
        <profile>
            <id>profile-2</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <dependencies>
                <dependency>
                    <groupId>group-two</groupId>
                    <artifactId>artifact-two</artifactId>
                    <version>1.3</version>
                </dependency>
            </dependencies>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'grandparent' }, _, MAVEN_POM) >> asResource(grandParent)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.3')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    def "uses dependency management section from imported POM in profile activated by absence of system property to define defaults for profile dependency"() {
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

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <property>
                    <name>!someProperty</name>
                </property>
            </activation>
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
        </profile>
    </profiles>
</project>
"""
        and:
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'imported' }, _, MAVEN_POM) >> asResource(imported)

        when:
        parsePom()

        then:
        def dep = firstDependency(metadata.dependencies)
        dep.selector == moduleId('group-two', 'artifact-two', '1.2')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
    }

    @Issue("GRADLE-3074")
    def "pom with packaging defined in active profile"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>artifact-one</artifactId>
    <version>version-one</version>
    <packaging>\${package.type}</packaging>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <package.type>war</package.type>
            </properties>
        </profile>
    </profiles>
</project>
"""

        when:
        def metaData = parseMetaData()

        then:
        metaData.packaging == 'war'
    }

    @Issue("GRADLE-3074")
    def "pom with packaging defined by custom property in active profile of parent pom"() {
        given:
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-one</groupId>
    <artifactId>parent</artifactId>
    <version>version-one</version>

    <profiles>
        <profile>
            <id>profile-1</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <package.type>war</package.type>
            </properties>
        </profile>
    </profiles>
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
        parseContext.getMetaDataArtifact({ it.getVariantSelector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        def metaData = parseMetaData()

        then:
        metaData.packaging == 'war'
    }
}
