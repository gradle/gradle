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

import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure

class MavenLatestResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "can use latest version selector with release versions"() {
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.0').publish()
        def moduleAA = mavenRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
dependencies { compile 'group:projectA:latest.foo' } // interestingly, status name doesn't matter
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        run 'retrieve'

        then:
        def buildDir = file('build')
        buildDir.assertHasDescendants(moduleAA.artifactFile.name)
    }

    def "cannot use latest version selector if highest version is snapshot (looks like a bug)"() {
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
        run 'retrieve'

        then:
        UnexpectedBuildFailure e = thrown()
        causes(e).any { it instanceof ModuleVersionNotFoundException }
    }

    def causes(e) {
        if (e == null) [] else [e] + causes(e.cause)
    }
}
