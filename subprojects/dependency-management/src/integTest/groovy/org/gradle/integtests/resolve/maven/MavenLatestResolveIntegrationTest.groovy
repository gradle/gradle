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
import spock.lang.Unroll

class MavenLatestResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "latest selector works correctly when no snapshot versions are present"() {
        given:
        mavenRepo().module('group', 'projectA', '1.0').publish()
        def highest = mavenRepo().module('group', 'projectA', '2.2').publish()
        mavenRepo().module('group', 'projectA', '1.4').publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:latest.$status' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants(highest.artifactFile.name)

        where:
        status << ["integration", "milestone", "release"]
    }

    def "latest selector with unknown status leads to failure"() {
        mavenRepo().module('group', 'projectA', '1.0').publish()

        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:latest.foo' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        expect:
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
configurations { compile }
repositories { maven { url "${mavenHttpRepo.uri}" } }
dependencies { compile 'group:projectA:latest.${status}' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run "retrieve"

        then:
        file("build").assertHasDescendants(latest)

        where:
        status        | latest
        "release"     | "projectA-1.0.jar"
        "milestone"   | "projectA-1.0.jar"
        "integration" | "projectA-1.2-SNAPSHOT.jar"
    }

    /**
     * TODO:
     * This acutally exposes an issue with the internal mapping of RELEASE
     * RELEASE should have status integration and not resolve the 2.0-SNAPSHOT but 1.5 version of project C.
     * */
    @Unroll
    def "can resolve dynamic #versionString version declared in pom as transitive dependency from HTTP Maven repository"() {
        given:
        mavenHttpRepo.module('org.test', 'projectC', '1.1').publish()
        mavenHttpRepo.module('org.test', 'projectC', '1.5').publish()
        mavenHttpRepo.module('org.test', 'projectC', '2.0-SNAPSHOT').publish()
        def projectC = mavenHttpRepo.module('org.test', 'projectC', '2.0').publish()
        def projectB = mavenHttpRepo.module('org.test', 'projectB', '1.0').dependsOn("org.test", 'projectC', versionString).publish()
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
        file('libs').assertHasDescendants('projectA-1.0.jar', 'projectB-1.0.jar', 'projectC-2.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then:
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)

        where:
        versionString << ["RELEASE", "LATEST"]
    }
}
