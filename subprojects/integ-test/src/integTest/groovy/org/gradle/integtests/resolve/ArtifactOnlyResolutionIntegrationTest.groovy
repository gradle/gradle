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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenModule
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class ArtifactOnlyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    @Rule public final TestResources resources = new TestResources();

    MavenModule projectA

    def "setup"() {
        requireOwnUserHomeDir()

        projectA = mavenRepo.module('group', 'projectA').publish()
        server.start()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0@jar'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""
    }

    def "can resolve and cache artifact-only dependencies from a HTTP repository"() {
        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        and:
        newRequestForModuleDoesNotContactServer()
    }

    def "can resolve and cache artifact-only dependencies from a HTTP repository with no descriptor"() {
        when:
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom')
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')

        and:
        newRequestForModuleDoesNotContactServer()
    }

    private newRequestForModuleDoesNotContactServer() {
        def snapshot = file('libs/projectA-1.0.jar').snapshot()
        server.resetExpectations()
        def result = run 'retrieve'
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
        return result
    }
}