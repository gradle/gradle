/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.basicBuildScriptSetup

class DependencyLockFileReportingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << basicBuildScriptSetup(mavenRepo)
    }

    def "dependency report indicates locked version"() {
        given:
        mavenRepo.module('other', 'dep', '0.1').publish()
        mavenRepo.module('other', 'dep', '0.2').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'other:dep:+'
            }
        """

        file('dependencies.lock').text = '[{"configuration":"myConf","dependencies":[{"requestedVersion":"+","coordinates":"other:dep","lockedVersion":"0.1"}]}]'

        when:
        run 'dependencies', '--configuration', 'myConf'

        then:
        output.contains("""myConf
\\--- other:dep:+ -> 0.1""")
    }

    def "dependency insight report indicates forced reason for locked version"() {
        given:
        mavenRepo.module('other', 'dep', '0.1').publish()
        mavenRepo.module('other', 'dep', '0.2').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'other:dep:+'
            }
        """

        file('dependencies.lock').text = '[{"configuration":"myConf","dependencies":[{"requestedVersion":"+","coordinates":"other:dep","lockedVersion":"0.1"}]}]'

        when:
        run 'dependencyInsight', '--configuration', 'myConf', '--dependency', 'other:dep'

        then:
        output.contains("""other:dep:0.1 (forced)

other:dep:+ -> 0.1
\\--- myConf""")
    }
}
