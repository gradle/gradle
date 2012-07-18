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

class MavenDynamicResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "can resolve dynamic version declared in pom as transitive dependency from HTTP Maven repository"() {
        given:
        server.start()

        mavenRepo().module('group', 'projectC', '1.1').publish()
        def projectC = mavenRepo().module('group', 'projectC', '1.5').publish()
        mavenRepo().module('group', 'projectC', '2.0').publish()
        def projectB = mavenRepo().module('group', 'projectB').dependsOn("group",'projectC', '[1.0, 2.0)').publish()
        def projectA = mavenRepo().module('group', 'projectA').dependsOn('projectB').publish()

        buildFile << """
    repositories {
        maven { url 'http://localhost:${server.port}/repo1' }
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
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)
        server.expectGet('/repo1/group/projectC/maven-metadata.xml', projectC.rootMetaDataFile)
        server.expectGet('/repo1/group/projectC/1.5/projectC-1.5.pom', projectC.pomFile)
        server.expectGet('/repo1/group/projectC/1.5/projectC-1.5.jar', projectC.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar', 'projectB-1.0.jar', 'projectC-1.5.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then:
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
    }
}
