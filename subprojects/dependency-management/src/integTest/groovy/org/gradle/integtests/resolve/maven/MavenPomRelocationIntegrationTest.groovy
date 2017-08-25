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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue
import spock.lang.Unroll

@Issue('https://github.com/gradle/gradle/issues/1789')
class MavenPomRelocationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Unroll
    def "can resolve relocated module"() {
        given:
        def original = publishPomWithRelocation('groupA', 'artifactA', relocationGroupId, relocationArtifactId)
        def newModule = mavenHttpRepo.module(newGroupId, newArtifactId, "1.0").publish()

        and:
        createBuildFileWithDependency('groupA', 'artifactA')

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("${newArtifactId}-1.0.jar")

        where:
        relocationGroupId | relocationArtifactId | newGroupId | newArtifactId
        'groupB'          | 'artifactB'          | 'groupB'   | 'artifactB'
        'groupB'          | null                 | 'groupB'   | 'artifactA'
        null              | 'artifactB'          | 'groupA'   | 'artifactB'
    }

    def "can resolve module from a nested relocation"() {
        given:
        def moduleA = publishPomWithRelocation('groupA', 'artifactA', 'groupB', 'artifactB')
        def moduleB = publishPomWithRelocation('groupB', 'artifactB', 'groupC', 'artifactC')
        def moduleC = mavenHttpRepo.module('groupC', 'artifactC', "1.0").publish()

        and:
        createBuildFileWithDependency('groupA', 'artifactA')

        and:
        moduleA.pom.expectGet()
        moduleB.pom.expectGet()
        moduleC.pom.expectGet()
        moduleC.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("libs").assertHasDescendants("artifactC-1.0.jar")
    }

    def "fails to resolve module if published artifact does not exist with relocated coordinates"() {
        given:
        def original = publishPomWithRelocation('groupA', 'artifactA', 'notExist', 'notExist')
        def newModule = mavenHttpRepo.module("notExist", "notExist", "1.0")

        and:
        createBuildFileWithDependency('groupA', 'artifactA')

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectHead()

        expect:
        fails "retrieve"
        failure.assertHasCause('Could not find notExist:notExist:1.0')
    }

    def createBuildFileWithDependency(String groupId, String artifactId) {
        buildFile << """
repositories { maven { url '${mavenHttpRepo.uri}' } }
configurations { compile }
dependencies { compile '${groupId}:${artifactId}:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
    }

    def publishPomWithRelocation(String groupId, String artifactId, String relocationGroupId, String relocationArtifactId) {
        def module = mavenHttpRepo.module(groupId, artifactId, '1.0').publishPom()
        def relocation = relocationGroupId ? "<groupId>${relocationGroupId}</groupId>" : ''
        relocation += relocationArtifactId ? "<artifactId>${relocationArtifactId}</artifactId>" : ''

        module.pomFile.text = """
<project>
    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>1.0</version>
    <distributionManagement>
        <relocation>
            ${relocation}
        </relocation>
    </distributionManagement>
</project>
"""
        return module
    }
}
