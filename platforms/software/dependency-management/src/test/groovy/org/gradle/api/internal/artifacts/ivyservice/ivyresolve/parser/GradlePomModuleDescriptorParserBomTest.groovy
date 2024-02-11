/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.component.external.descriptor.MavenScope

import static org.gradle.api.internal.component.ArtifactType.MAVEN_POM

class GradlePomModuleDescriptorParserBomTest extends AbstractGradlePomModuleDescriptorParserTest {

    def "a pom file with packaging=pom is a bom - dependencies declared in dependencyManagement block are treated as optional non-transitive dependencies"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-b', 'module-b', '1.0')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
        dep.constraint
        !dep.transitive
    }

    def "a bom can combine dependencies and dependencyManagement constraints"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencies>
        <dependency>
            <groupId>group-a</groupId>
            <artifactId>module-a</artifactId>
            <version>1.0</version>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>2.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        when:
        parsePom()

        then:
        def depA = metadata.dependencies.find { it.selector.group == 'group-a'}
        depA.selector == moduleId('group-a', 'module-a', '1.0')
        !depA.optional
        depA.transitive
        def depB = metadata.dependencies.find { it.selector.group == 'group-b'}
        depB.selector == moduleId('group-b', 'module-b', '2.0')
        depB.constraint
        !depB.transitive
    }

    def "dependency management block from parent pom is inherited"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>module-a</artifactId>
    <version>1.0</version>

    <parent>
        <groupId>group-a</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
    </parent>
</project>
"""
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        metadata.dependencies.size() == 1
        metadata.dependencies[0].constraint
        metadata.dependencies[0].selector.module == 'module-b'
        metadata.dependencies[0].selector.version == '1.0'
    }

    def "a bom can have a parent pom - dependencyManagement entries are combined"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>module-a</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <parent>
        <groupId>group-a</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-c</groupId>
                <artifactId>module-c</artifactId>
                <version>2.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)

        when:
        parsePom()

        then:
        def depC = metadata.dependencies.find { it.selector.group == 'group-c'}
        depC.selector == moduleId('group-c', 'module-c', '2.0')
        depC.constraint
        !depC.transitive
        def depB = metadata.dependencies.find { it.selector.group == 'group-b'}
        depB.selector == moduleId('group-b', 'module-b', '1.0')
        depB.constraint
        !depB.transitive
    }

    def "an entry in the dependencyManagement block without version does not fail parsing"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-b', 'module-b', '')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
        dep.constraint
        !dep.transitive
    }

    def "a bom can declare a constraint with excludes"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
                <exclusions>
                    <exclusion>
                        <groupId>group-c</groupId>
                        <artifactId>module-c</artifactId>
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
        def dep = single(metadata.dependencies)
        dep.allExcludes[0].moduleId.group == 'group-c'
        dep.allExcludes[0].moduleId.name == 'module-c'
        dep.allExcludes[0].artifact == null
    }

    def "a bom version can be relocated"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <distributionManagement>
        <relocation>
            <version>2.0</version>
        </relocation>
    </distributionManagement>
</project>
"""

        def relocatedToPomFile = tmpDir.file("relocated.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>2.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        parseContext.getMetaDataArtifact({ it.version == '2.0' }, MAVEN_POM) >> asResource(relocatedToPomFile)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-b', 'module-b', '1.0')
        dep.scope == MavenScope.Compile
        hasDefaultDependencyArtifact(dep)
        dep.constraint
    }

    def "a bom can be composed of children and parents"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <parent>
        <groupId>group-a</groupId>
        <artifactId>parent</artifactId>
        <version>1.0</version>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        def parent = tmpDir.file("parent.xml") << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-c</groupId>
                <artifactId>module-c</artifactId>
                <version>2.0</version>
            </dependency>
            <dependency>
                <groupId>group-d</groupId>
                <artifactId>module-d</artifactId>
                <version>2.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        parseContext.getMetaDataArtifact({ it.selector.module == 'parent' }, _, MAVEN_POM) >> asResource(parent)
        when:
        parsePom()

        then:
        metadata.dependencies.size() == 3
        metadata.dependencies.each { assert it.constraint }
    }

    def "scopes defined in a bom are ignored"() {
        given:
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-b', 'module-b', '1.0')
        dep.scope == MavenScope.Compile //compile is the 'default' scope
        hasDefaultDependencyArtifact(dep)
        dep.constraint
        !dep.transitive
    }

    def 'exclusion on imported BOM is ignored'() {
        given:
        def bomFile = tmpDir.file('bom.xml') << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-b</groupId>
                <artifactId>module-b</artifactId>
                <version>1.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        pomFile << """
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>group-a</groupId>
    <artifactId>project</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>group-a</groupId>
                <artifactId>bom</artifactId>
                <version>1.0</version>
                <scope>import</scope>
                <type>pom</type>
                <exclusions>
                    <exclusion>
                        <groupId>group-b</groupId>
                        <artifactId>module-b</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
"""
        parseContext.getMetaDataArtifact({ it.selector.module == 'bom' }, _, MAVEN_POM) >> asResource(bomFile)

        when:
        parsePom()

        then:
        def dep = single(metadata.dependencies)
        dep.selector == moduleId('group-b', 'module-b', '1.0')
        dep.constraint

    }
}
