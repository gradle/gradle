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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class CachedMissingModulesIntegrationTest extends AbstractDependencyResolutionTest {

    def "cached not-found information is ignored if module is not available in any repo"() {
        given:
        server.start()

        mavenRepo("repo2")

        buildFile << """
    repositories {
        maven {
            name 'repo1'
            url 'http://localhost:${server.port}/repo1'
        }
        maven {
            name 'repo2'
            url 'http://localhost:${server.port}/repo2'
        }
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

        when:
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom')
        server.expectGetMissing('/repo2/group/projectA/1.0/projectA-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.jar')
        server.expectHeadMissing('/repo2/group/projectA/1.0/projectA-1.0.jar')

        then:
        runAndFail 'retrieve'

        when:
        server.resetExpectations()
        def projectA = mavenRepo("repo2").module('group', 'projectA').publish()

        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0.jar')

        server.expectGet("/repo2/group/projectA/1.0/projectA-1.0.pom", projectA.pomFile)
        server.expectGet("/repo2/group/projectA/1.0/projectA-1.0.jar", projectA.artifactFile)

        then:
        run 'retrieve'//

        when:
        server.resetExpectations()
        then:
        executer.withArgument("--rerun-tasks")
        run 'retrieve'
    }
}
