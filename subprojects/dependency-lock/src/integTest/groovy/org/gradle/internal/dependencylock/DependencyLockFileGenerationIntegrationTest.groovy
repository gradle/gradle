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

import org.gradle.api.plugins.dependencylock.DependencyLockPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.basicBuildScriptSetup

class DependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << basicBuildScriptSetup(mavenRepo)
    }

    def "can create locks for all supported dynamic version formats"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'foo:bar:1.+'
                myConf 'org:gradle:+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"},{"requestedVersion":"+","coordinates":"org:gradle","lockedVersion":"7.5"},{"requestedVersion":"latest.release","coordinates":"my:prod","lockedVersion":"3.2.1"},{"requestedVersion":"[1.0,2.0]","coordinates":"dep:range","lockedVersion":"1.7.1"}]}]'
    }

    def "can create locks for all configurations"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()

        buildFile << """
            apply plugin: 'java'

            dependencies {
                compile 'foo:bar:1.+'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"configuration":"compile","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"compileClasspath","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"default","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"runtime","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"runtimeClasspath","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"testCompile","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"testCompileClasspath","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"testRuntime","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"configuration":"testRuntimeClasspath","dependencies":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]}]'
    }

    def "can create locks for dependencies with a concrete version"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:bar","lockedVersion":"1.5"}]}]'
    }

    def "can create locks for first-level and transitive dependencies"() {
        given:
        def fooThirdModule = mavenRepo.module('foo', 'third', '1.5').publish()
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').dependsOn(fooThirdModule).publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        def barThirdModule = mavenRepo.module('bar', 'third', '2.5').publish()
        def barSecondModule = mavenRepo.module('bar', 'second', '2.6.7').dependsOn(barThirdModule).publish()
        mavenRepo.module('bar', 'first', '2.5').dependsOn(barSecondModule).publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'bar:first:2.+'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:first","lockedVersion":"1.5"},{"requestedVersion":"2.+","coordinates":"bar:first","lockedVersion":"2.5"},{"requestedVersion":"1.6.7","coordinates":"foo:second","lockedVersion":"1.6.7"},{"requestedVersion":"1.5","coordinates":"foo:third","lockedVersion":"1.5"},{"requestedVersion":"2.6.7","coordinates":"bar:second","lockedVersion":"2.6.7"},{"requestedVersion":"2.5","coordinates":"bar:third","lockedVersion":"2.5"}]}]'
    }

    def "writes lock for conflict-resolved dependency version"() {
        given:
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        mavenRepo.module('foo', 'second', '1.9').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'foo:second:1.9'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"configuration":"myConf","dependencies":[{"requestedVersion":"1.5","coordinates":"foo:first","lockedVersion":"1.5"},{"requestedVersion":"1.9","coordinates":"foo:second","lockedVersion":"1.9"}]}]'
    }
}
