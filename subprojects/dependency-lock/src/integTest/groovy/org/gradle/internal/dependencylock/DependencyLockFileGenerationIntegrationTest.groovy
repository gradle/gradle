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
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.*

class DependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    TestFile lockFile

    def setup() {
        buildFile << basicBuildScriptSetup(mavenRepo)
        lockFile = file('gradle/dependencies.lock')
    }

    def "does not write lock file if no dependencies were resolved"() {
        given:
        buildFile << customConfiguration('myConf')
        buildFile << copyLibsTask('myConf')

        when:
        succeeds('copyLibs')

        then:
        !lockFile.exists()
    }

    def "can create locks for dependencies with concrete version"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << customConfiguration('myConf')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask('myConf')

        when:
        succeeds('copyLibs')

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:bar","lockedVersion":"1.5"}]}]'
    }

    def "can create locks for all supported formats of dynamic dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << customConfiguration('myConf')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                myConf 'org:gradle:+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """
        buildFile << copyLibsTask('myConf')

        when:
        succeeds('copyLibs')

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"},{"requestedVersion":"+","coordinates":"org:gradle","lockedVersion":"7.5"},{"requestedVersion":"latest.release","coordinates":"my:prod","lockedVersion":"3.2.1"},{"requestedVersion":"[1.0,2.0]","coordinates":"dep:range","lockedVersion":"1.7.1"}]}]'
    }

    def "only creates locks for resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << customConfiguration('myConf')
        buildFile << customConfiguration('unresolved')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                unresolved 'org:gradle:+'
            }
        """
        buildFile << copyLibsTask('myConf')

        when:
        succeeds('copyLibs')

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]}]'
    }
}
