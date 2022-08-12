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

@Issue('https://github.com/gradle/gradle/issues/1789')
class MavenPomRelocationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        file("compileClasspath").mkdir()
        file("runtimeClasspath").mkdir()
    }

    def "can resolve relocated module"() {
        given:
        def original = publishPomWithRelocation('groupA', 'artifactA', relocationGroupId, relocationArtifactId)
        def apiDep = mavenHttpRepo.module("org", "api", "1.0").publish()
        def implDep = mavenHttpRepo.module("org", "impl", "1.0").publish()
        def newModule = mavenHttpRepo.module(newGroupId, newArtifactId, "1.0")
            .dependsOn(apiDep, scope: 'compile')
            .dependsOn(implDep, scope: 'runtime')
            .publish()

        and:
        createBuildFileWithDependency('groupA', 'artifactA')

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        newModule.artifact.expectGet()
        apiDep.pom.expectGet()
        apiDep.artifact.expectGet()
        implDep.pom.expectGet()
        implDep.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("compileClasspath").assertHasDescendants("${newArtifactId}-1.0.jar", "api-1.0.jar")
        file("runtimeClasspath").assertHasDescendants("${newArtifactId}-1.0.jar", "api-1.0.jar", "impl-1.0.jar")

        where:
        relocationGroupId | relocationArtifactId | newGroupId | newArtifactId
        'groupB'          | 'artifactB'          | 'groupB'   | 'artifactB'
        'groupB'          | null                 | 'groupB'   | 'artifactA'
        null              | 'artifactB'          | 'groupA'   | 'artifactB'
    }

    def "can resolve module with relocated version"() {
        given:
        def moduleB = mavenHttpRepo.module('groupB', 'artifactB').publish()
        def original = publishPomWithRelocation('groupA', 'artifactA', 'groupA', 'artifactA', '2.0')
        def newModule = mavenHttpRepo.module('groupA', 'artifactA', '2.0').dependsOn(moduleB).publish()

        and:
        createBuildFileWithDependency('groupA', 'artifactA')

        and:
        original.pom.expectGet()
        newModule.pom.expectGet()
        moduleB.pom.expectGet()
        moduleB.artifact.expectGet()

        when:
        run "retrieve"

        then:
        file("compileClasspath").assertHasDescendants("artifactB-1.0.jar")
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
        file("compileClasspath").assertHasDescendants("artifactC-1.0.jar")
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

        expect:
        fails "retrieve"
        failure.assertHasCause('Could not find notExist:notExist:1.0')
    }

    def createBuildFileWithDependency(String groupId, String artifactId) {
        buildFile << """
apply plugin: 'java-library'
repositories { maven { url '${mavenHttpRepo.uri}' } }
dependencies { implementation '${groupId}:${artifactId}:1.0' }
task retrieve {}
['compile', 'runtime'].each { cp ->
   retrieve.dependsOn(tasks.create("retrieve\${cp.capitalize()}Classpath", Sync) {
      into "\${cp}Classpath"
      from configurations.getByName("\${cp}Classpath")
   })
}
"""
    }

    def publishPomWithRelocation(String groupId, String artifactId, String relocationGroupId, String relocationArtifactId, String relocationVersion = null) {
        def module = mavenHttpRepo.module(groupId, artifactId, '1.0').publishPom()
        def relocation = relocationGroupId ? "<groupId>${relocationGroupId}</groupId>" : ''
        relocation += relocationArtifactId ? "<artifactId>${relocationArtifactId}</artifactId>" : ''
        relocation += relocationVersion ? "<version>${relocationVersion}</version>" : ''

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
