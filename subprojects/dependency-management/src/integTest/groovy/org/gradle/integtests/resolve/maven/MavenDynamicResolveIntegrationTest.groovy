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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class MavenDynamicResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "can resolve snapshot versions with version range"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile group: "org.test", name: "projectA", version: "1.+"
    compile group: "org.test", name: "projectB", version: "1.+"
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        mavenHttpRepo.module("org.test", "projectA", "1.0").publish()
        mavenHttpRepo.module("org.test", "projectA", "1.1-SNAPSHOT").publish()
        def matchingA = mavenHttpRepo.module("org.test", "projectA", "1.1").publish()
        mavenHttpRepo.module("org.test", "projectA", "2.0").publish()

        mavenHttpRepo.module("org.test", "projectB", "1.1").publish()
        def matchingB = mavenHttpRepo.module("org.test", "projectB", "1.2-SNAPSHOT").publish()
        mavenHttpRepo.module("org.test", "projectB", "2.0").publish()

        and:
        mavenHttpRepo.getModuleMetaData("org.test", "projectA").expectGet()
        matchingA.pom.expectGet()
        matchingA.artifact.expectGet()

        mavenHttpRepo.getModuleMetaData("org.test", "projectB").expectGet()
        matchingB.metaData.expectGet()
        matchingB.pom.expectGet()
        matchingB.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.2-SNAPSHOT.jar')
        file('libs/projectA-1.1.jar').assertIsCopyOf(matchingA.artifactFile)
        file('libs/projectB-1.2-SNAPSHOT.jar').assertIsCopyOf(matchingB.artifactFile)

        when:
        server.resetExpectations()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar', 'projectB-1.2-SNAPSHOT.jar')
    }

    def "can resolve dynamic version declared in pom as transitive dependency from HTTP Maven repository"() {
        given:
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
        projectA.pom.expectGet()
        projectA.getArtifact().expectGet()
        projectB.pom.expectGet()
        projectB.getArtifact().expectGet()
        mavenHttpRepo.getModuleMetaData("org.test", "projectC").expectGet()
        projectC.pom.expectGet()
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
        mavenHttpRepo.getModuleMetaData("org.test", "projectA").expectGetMissing()
        mavenHttpRepo.directory("org.test", "projectA").expectGet()
        projectA.pom.expectGet()
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

    def "reports and recovers from broken maven-metadata.xml and directory listing"() {
        given:
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
        def metaData = mavenHttpRepo.getModuleMetaData("org.test", "projectA")
        metaData.expectGetBroken()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause('Could not resolve org.test:projectA:1.+.')
        failure.assertHasCause('Failed to list versions for org.test:projectA.')
        failure.assertHasCause("Unable to load Maven meta-data from ${metaData.uri}.")
        failure.assertHasCause("Could not GET '${metaData.uri}'. Received status code 500 from server")

        when:
        metaData.expectGetMissing()

        def moduleDir = mavenHttpRepo.directory("org.test", "projectA")
        moduleDir.expectGetBroken()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause('Could not resolve org.test:projectA:1.+.')
        failure.assertHasCause('Failed to list versions for org.test:projectA.')
        failure.assertHasCause("Could not list versions using M2 pattern '${mavenHttpRepo.uri}")
        failure.assertHasCause("Could not GET '${moduleDir.uri}'. Received status code 500 from server")

        when:
        server.resetExpectations()
        metaData.expectGet()
        projectA.pom.expectGet()
        projectA.artifact.expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('projectA-1.5.jar')
    }

    def "dynamic version reports and recovers from broken module"() {
        given:
        def repo = mavenHttpRepo("repo1")
        def projectA = repo.module('group', 'projectA', '1.1').publish()

        buildFile << """
        repositories {
            maven { url '${repo.uri}' }
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
        repo.getModuleMetaData("group", "projectA").expectGet()
        projectA.pom.expectGetBroken()

        and:
        fails 'retrieve'

        then:
        failure.assertHasCause("Could not resolve group:projectA:1.+.")
        failure.assertHasCause("Could not GET '${projectA.pom.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        projectA.pom.expectGet()
        projectA.artifact.expectGetBroken()

        and:
        fails 'retrieve'

        then:
        failure.assertHasCause("Could not download projectA.jar (group:projectA:1.1)")
        failure.assertHasCause("Could not GET '${projectA.artifact.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        projectA.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    def "dynamic version reports and recovers from missing module"() {
        given:
        def repo = mavenHttpRepo("repo1")
        def projectA = repo.module('group', 'projectA', '1.1').publish()

        buildFile << """
        repositories {
            maven { url '${repo.uri}' }
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
        repo.getModuleMetaData("group", "projectA").expectGet()
        projectA.pom.expectGetMissing()
        projectA.artifact.expectHeadMissing()

        and:
        fails 'retrieve'

        then:
        // TODO - this error message isn't right: it found a version, it just happened to be missing. should really choose another version
        failure.assertHasCause("""Could not find any matches for group:projectA:1.+ as no versions of group:projectA are available.
Searched in the following locations:
    ${repo.getModuleMetaData("group", "projectA").uri}
    ${projectA.pom.uri}
    ${projectA.artifact.uri}
Required by:
""")

        when:
        server.resetExpectations()
        projectA.pom.expectGet()
        projectA.artifact.expectGetMissing()

        and:
        fails 'retrieve'

        then:
        failure.assertHasCause("""Could not find projectA.jar (group:projectA:1.1).
Searched in the following locations:
    ${projectA.artifact.uri}""")

        when:
        server.resetExpectations()
        repo.getModuleMetaData("group", "projectA").expectHead()
        projectA.pom.expectHead()
        projectA.artifact.expectGet()

        and:
        args("--refresh-dependencies")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')
    }

    def "dynamic version ignores broken module in one repository when available in another repository"() {
        given:
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
        repo1.getModuleMetaData("group", "projectA").expectGet()
        projectA1.pom.expectGet()
        projectA1.getArtifact().expectGet()

        repo2.getModuleMetaData("group", "projectA").expectGet()
        projectA2.pom.expectGetBroken()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.1.jar')

        when:
        server.resetExpectations()
        projectA2.pom.expectGet()
        projectA2.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.5.jar')
    }
}
