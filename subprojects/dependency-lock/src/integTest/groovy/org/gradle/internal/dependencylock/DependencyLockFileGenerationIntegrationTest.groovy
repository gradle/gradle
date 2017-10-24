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

    private static final String MYCONF_CUSTOM_CONFIGURATION = 'myConf'

    TestFile lockFile

    def setup() {
        buildFile << basicBuildScriptSetup(mavenRepo)
        lockFile = file('gradle/dependencies.lock')
    }

    def "does not write lock file if no dependencies were resolved"() {
        given:
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        !lockFile.exists()
    }

    def "can create locks for dependencies with concrete version"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:bar","lockedVersion":"1.5"}]}]'
    }

    def "can create locks for all supported formats of dynamic dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                myConf 'org:gradle:+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"},{"requestedVersion":"+","coordinates":"org:gradle","lockedVersion":"7.5"},{"requestedVersion":"latest.release","coordinates":"my:prod","lockedVersion":"3.2.1"},{"requestedVersion":"[1.0,2.0]","coordinates":"dep:range","lockedVersion":"1.7.1"}]}]'
    }

    def "only creates locks for resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION, 'unresolved')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                unresolved 'org:gradle:+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]}]'
    }

    def "can create locks for all multiple resolved configurations"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()

        buildFile << customConfigurations('a', 'b', 'c')
        buildFile << """
            dependencies {
                a 'foo:bar:1.+'
                b 'org:gradle:7.5'
                c 'my:prod:latest.release'
            }
        """
        buildFile << copyLibsTask('a', 'b', 'c')

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == '[{"configuration":"a","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"b","dependencies":[{"requestedVersion":"7.5","coordinates":"org:gradle","lockedVersion":"7.5"}]},{"configuration":"c","dependencies":[{"requestedVersion":"latest.release","coordinates":"my:prod","lockedVersion":"3.2.1"}]}]'
    }

    def "can create locks for first-level and transitive resolved dependencies"() {
        given:
        def fooThirdModule = mavenRepo.module('foo', 'third', '1.5').publish()
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').dependsOn(fooThirdModule).publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        def barThirdModule = mavenRepo.module('bar', 'third', '2.5').publish()
        def barSecondModule = mavenRepo.module('bar', 'second', '2.6.7').dependsOn(barThirdModule).publish()
        mavenRepo.module('bar', 'first', '2.5').dependsOn(barSecondModule).publish()

        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'bar:first:2.+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:first","lockedVersion":"1.5"},{"requestedVersion":"1.6.7","coordinates":"foo:second","lockedVersion":"1.6.7"},{"requestedVersion":"1.5","coordinates":"foo:third","lockedVersion":"1.5"},{"requestedVersion":"2.+","coordinates":"bar:first","lockedVersion":"2.5"},{"requestedVersion":"2.6.7","coordinates":"bar:second","lockedVersion":"2.6.7"},{"requestedVersion":"2.5","coordinates":"bar:third","lockedVersion":"2.5"}]}]'
    }
}
