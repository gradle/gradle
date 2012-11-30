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

        mavenHttpRepo.module('org.test', 'projectC', '1.1').publish()
        def projectC = mavenHttpRepo.module('org.test', 'projectC', '1.5').publish()
        mavenHttpRepo.module('org.test', 'projectC', '2.0').publish()
        def projectB = mavenHttpRepo.module('org.test', 'projectB', '1.0').dependsOn("org.test", 'projectC', '[1.0, 2.0)').publish()
        def projectA = mavenHttpRepo.module('org.test', 'projectA', '1.0').dependsOn('org.test', 'projectB', '1.0').publish()

        buildFile << """
    repositories {
        maven { url '${mavenHttpRepo.uri}' }
    }
    configurations { compile }
    dependencies {
        compile 'org.test:projectA:1.0'
    }

    task retrieve(type: Sync) {
        into 'libs'
        from configurations.compile
    }
    """

        when:
        projectA.expectPomGet()
        projectA.getArtifact().expectGet()
        projectB.expectPomGet()
        projectB.getArtifact().expectGet()
        mavenHttpRepo.expectMetaDataGet("org.test", "projectC")
        projectC.expectPomGet()
        projectC.getArtifact().expectGet()

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

    def "falls back to directory listing when maven-metadata.xml is missing"() {
        given:
        server.start()
        mavenHttpRepo.module('org.test', 'projectA', '1.0').publish()
        def projectA = mavenHttpRepo.module('org.test', 'projectA', '1.5').publish()

        buildFile << """
    repositories {
        maven { url '${mavenHttpRepo.uri}' }
    }
    configurations { compile }
    dependencies {
        compile 'org.test:projectA:1.+'
    }

    task retrieve(type: Sync) {
        into 'libs'
        from configurations.compile
    }
    """

        when:
        mavenHttpRepo.expectMetaDataGetMissing("org.test", "projectA")
        mavenHttpRepo.expectDirectoryListGet("org.test", "projectA")
        projectA.expectPomGet()
        projectA.getArtifact().expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.5.jar')
        def snapshot = file('libs/projectA-1.5.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then:
        file('libs/projectA-1.5.jar').assertHasNotChangedSince(snapshot)
    }

    def "does not cache broken module information"() {
        given:
        server.start()
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")
        def projectA1 = repo1.module('group', 'projectA', '1.1').publish()
        def projectA2 = repo2.module('group', 'projectA', '1.5').publish()

        buildFile << """
        repositories {
            maven { url '${repo1.uri}' }
            maven { url '${repo2.uri}' }
        }
        configurations { compile }
        dependencies {
            compile 'group:projectA:1.+'
        }

        task retrieve(type: Sync) {
            into 'libs'
            from configurations.compile
        }
        """

        when:
        repo1.expectMetaDataGet("group", "projectA")
        projectA1.expectPomGet()
        projectA1.getArtifact().expectGet()

        repo2.expectMetaDataGet("group", "projectA")
        server.addBroken('/repo2/group/projectA/1.5/projectA-1.5.pom')

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when:
        server.resetExpectations()
        repo2.expectMetaDataGet("group", "projectA")
        projectA2.expectPomGet()
        projectA2.getArtifact().expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.5.jar')
    }
}
