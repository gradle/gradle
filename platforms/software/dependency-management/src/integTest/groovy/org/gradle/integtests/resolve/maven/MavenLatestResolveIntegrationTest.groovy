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

class MavenLatestResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        buildFile << """
            repositories {
                maven { url = '${mavenRepo().uri}' }
            }
            configurations { compile }

            task retrieve(type: Sync) {
                into 'build'
                from configurations.compile
            }
        """
    }

    def runRetrieveTask() {
        executer.withArgument("--no-problems-report")
        run 'retrieve'
    }
    def "latest selector works correctly when no snapshot versions are present"() {
        given:
        mavenRepo().module('group', 'projectA', '1.0').publish()
        def highest = mavenRepo().module('group', 'projectA', '2.2').publish()
        mavenRepo().module('group', 'projectA', '1.4').publish()

        and:
        buildFile << " dependencies { compile 'group:projectA:latest.$status' }"

        when:
        runRetrieveTask()

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants(highest.artifactFile.name)

        where:
        status << ["integration", "milestone", "release"]
    }

    def "latest selector with unknown status leads to failure"() {
        mavenRepo().module('group', 'projectA', '1.0').publish()

        buildFile << "dependencies { compile 'group:projectA:latest.foo' }"

        expect:
        executer.withArgument("--no-problems-report")
        fails 'retrieve'
        // would be better if metadata validation failed (status not contained in status scheme)
        failure.assertHasCause("Could not find any version that matches group:projectA:latest.foo.")
    }

    def "snapshot versions are considered integration status when using latest selector"() {
        given:
        mavenHttpRepo.getModuleMetaData('group', 'projectA').allowGetOrHead()
        mavenHttpRepo.module('group', 'projectA', '1.0').publish().allowAll()
        mavenHttpRepo.module('group', 'projectA', '1.2-SNAPSHOT').publish().allowAll()

        and:
        buildFile << """
            repositories { maven { url = "${mavenHttpRepo.uri}" } }
            dependencies { compile 'group:projectA:latest.${status}' }
        """

        when:
        runRetrieveTask()

        then:
        file("build").assertHasDescendants(latest)

        where:
        status        | latest
        "release"     | "projectA-1.0.jar"
        "milestone"   | "projectA-1.0.jar"
        "integration" | "projectA-1.2-SNAPSHOT.jar"
    }

    def "can resolve dynamic #versionDefinition version declared in pom as transitive dependency"() {
        given:
        mavenRepo().module('org.test', 'projectC', '1.1').publish()
        mavenRepo().module('org.test', 'projectC', '1.5').publish()
        mavenRepo().module('org.test', 'projectC', '2.0').publish()
        // We use a non-unique snapshot here because we are using a file repository, and we want the file name to be projectC-2.1-SNAPSHOT.jar
        // For a file repository, the resolved artifact name would be the unique snapshot version (since we use artifact in-place)
        mavenRepo().module('org.test', 'projectC', '2.1-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo().module('org.test', 'projectB', '1.0').dependsOn("org.test", 'projectC', versionDefinition).publish()
        mavenRepo().module('org.test', 'projectA', '1.0').dependsOn('org.test', 'projectB', '1.0').publish()

        buildFile << """
            dependencies {
                compile 'org.test:projectA:1.0'
            }"""

        when:
        runRetrieveTask()

        then:
        file('build').assertHasDescendants('projectA-1.0.jar', 'projectB-1.0.jar', "projectC-${resolvedVersion}.jar")

        where:
        versionDefinition | resolvedVersion
        "RELEASE"         | "2.0"
        "LATEST"          | "2.1-SNAPSHOT"
    }
}
