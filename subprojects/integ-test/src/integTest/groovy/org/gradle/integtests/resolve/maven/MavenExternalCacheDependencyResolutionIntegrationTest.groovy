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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Rule
import org.gradle.integtests.fixtures.MavenModule

class MavenExternalCacheDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    @Rule public final TestResources resources = new TestResources();

    def "setup"() {
        requireOwnUserHomeDir()
    }

    def "does not re-download artifact downloaded from a different URI when sha1 matches"() {
        given:
        def projectB = repo().module('group', 'projectB').publish()
        moduleIsDownloadedFromRepository1(projectB)

        when:
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom.sha1', projectB.sha1File(projectB.pomFile))
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar.sha1', projectB.sha1File(projectB.artifactFile))

        then:
        succeedsWithRepository2 'retrieve'
    }

    def "does re-download artifact downloaded from a different URI when sha1 not found"() {
        given:
        def projectB = repo().module('group', 'projectB').publish()
        moduleIsDownloadedFromRepository1(projectB)

        when:
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.pom.sha1')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.jar.sha1')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWithRepository2 'retrieve'
    }

    def "does re-download artifact downloaded from a different URI when sha1 does not match"() {
        given:
        def projectB = repo().module('group', 'projectB').publish()
        moduleIsDownloadedFromRepository1(projectB)

        when:
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom.sha1', projectB.md5File(projectB.pomFile))
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar.sha1', projectB.md5File(projectB.artifactFile))
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeedsWithRepository2 'retrieve'
    }


    private moduleIsDownloadedFromRepository1(MavenModule projectB) {
        server.start()

        buildFile << """
repositories {
if (project.hasProperty('repository2')) {
    maven { url 'http://localhost:${server.port}/repo2' }
} else {
    maven { url 'http://localhost:${server.port}/repo1' }
}
}
configurations { compile }
dependencies {
    compile 'group:projectB:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectB-1.0.jar')
        server.resetExpectations()
    }

    def succeedsWithRepository2(task) {
        executer.withArguments('-i', '-Prepository2')
        succeeds 'retrieve'
    }

    MavenRepository repo() {
        return new MavenRepository(file('repo'))
    }
}