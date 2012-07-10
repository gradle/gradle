/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import spock.lang.Issue
import org.gradle.integtests.fixtures.MavenModule

class MavenPomPackagingDependencyResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    MavenModule projectA

    def setup() {
        server.start()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
    maven { url 'http://localhost:${server.port}/repo2' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0'
}
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        projectA = mavenRepo().module('group', 'projectA')
    }

    def "looks for jar artifact for pom with packing of type 'pom' in the same repository only"() {
        when:
        publishWithPackaging('pom')

        and:
        // First attempts to resolve in repo1
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.jar')

        server.expectGet('/repo2/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHead('/repo2/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then: // Uses cached artifacts
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
    }

    def "will use jar artifact for pom with packing that maps to jar"() {
        when:
        publishWithPackaging(packaging)

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectA.artifactFile)

        where:
        packaging << ['', 'jar', 'eclipse-plugin', 'bundle']
    }


    @Issue('GRADLE-2188')
    def "will use jar artifact for pom with packing 'orbit'"() {
        when:
        publishWithPackaging('orbit')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.orbit')
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        file('libs/projectA-1.0.jar').assertIsCopyOf(projectA.artifactFile)
    }

    @Issue('GRADLE-2188')
    def "where 'module.custom' exists, will use it as main artifact for pom with packing 'custom' and emit deprecation warning"() {
        when:
        publishWithPackaging('custom', 'custom')

        and:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.custom', projectA.artifactFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.custom', projectA.artifactFile)

        and:
        executer.withDeprecationChecksDisabled()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.0.custom')
        file('libs/projectA-1.0.custom').assertIsCopyOf(projectA.artifactFile)

        and:
        result.output.contains("Deprecated: relying on packaging to define the extension of the main artifact is deprecated")
    }

    private def publishWithPackaging(String packaging, String type = 'jar') {
        projectA.packaging = packaging
        projectA.type = type
        projectA.publish()
    }

}