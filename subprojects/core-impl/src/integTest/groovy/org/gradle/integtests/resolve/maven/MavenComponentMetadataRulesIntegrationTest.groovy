/*
 * Copyright 2013 the original author or authors.
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

class MavenComponentMetadataRulesIntegrationTest extends AbstractDependencyResolutionTest {
    def "rules are provided with correct metadata"() {
        given:
        mavenRepo().module('group1', 'projectA', '1.0').publish()
        mavenRepo().module('group2', 'projectB', '2.0-SNAPSHOT').publish()

        and:
        buildFile << """
configurations { compile }
repositories { maven { url "${mavenRepo().uri}" } }
def allDetails = []
dependencies {
    compile 'group1:projectA:1.0'
    compile 'group2:projectB:2.0-SNAPSHOT'
    components {
        eachComponent { allDetails << it }
    }
}

task verify << {
    configurations.compile.resolve()

    def projectA = allDetails.find { it.id.name == 'projectA' }
    assert projectA != null
    assert projectA.id.group == 'group1'
    assert projectA.id.version == '1.0'
    assert projectA.status == 'release'
    assert projectA.statusScheme == ['integration', 'milestone', 'release']

    def projectB = allDetails.find { it.id.name == 'projectB' }
    assert projectB != null
    assert projectB.id.group == 'group2'
    assert projectB.id.version == '2.0-SNAPSHOT'
    assert projectB.status == 'integration'
    assert projectB.statusScheme == ['integration', 'milestone', 'release']
}
"""

        expect:
        succeeds 'verify'
    }
}
