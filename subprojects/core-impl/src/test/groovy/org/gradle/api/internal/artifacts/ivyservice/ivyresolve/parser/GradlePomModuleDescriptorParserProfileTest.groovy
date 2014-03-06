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

import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource

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
        and:
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')

        when:
        def descriptor = parsePom()

        then:
        descriptor.moduleRevisionId == moduleId('group-one', 'artifact-one', 'version-one')
        hasArtifact(descriptor, 'artifact-one', 'jar', 'jar')
        descriptor.dependencies.length == 0
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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')
        parseContext.getArtifact(_) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
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
        parseContext.currentRevisionId >> moduleId('group-one', 'artifact-one', 'version-one')
        parseContext.getArtifact({ it.id.moduleVersionIdentifier.name == 'parent' }) >> { new DefaultLocallyAvailableExternalResource(parent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(parent)) }
        parseContext.getArtifact({ it.id.moduleVersionIdentifier.name == 'grandparent' }) >> { new DefaultLocallyAvailableExternalResource(grandParent.toURI().toURL().toString(), new DefaultLocallyAvailableResource(grandParent)) }

        when:
        def descriptor = parsePom()

        then:
        descriptor.dependencies.length == 1
        def dep = descriptor.dependencies.first()
        dep.dependencyRevisionId == moduleId('group-two', 'artifact-two', 'version-two')
        dep.moduleConfigurations == ['compile', 'runtime']
        hasDefaultDependencyArtifact(dep)
    }
}
