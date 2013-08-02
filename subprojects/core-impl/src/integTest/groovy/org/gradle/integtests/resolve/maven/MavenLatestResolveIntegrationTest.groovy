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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class MavenLatestResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "latest selector works correctly when (only) release versions are present"() {
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

    // describes the actual (not the desired) behavior
    def "latest selector doesn't work correctly if highest version is snapshot"() {
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.0').publish()
        def moduleAA = mavenRepo().module('group', 'projectA', '1.2-SNAPSHOT').publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:latest.release' }
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        runAndFail("retrieve")

        then:
        failure.assertHasCause("Could not find any version that matches group:projectA:latest.release")
    }
}
