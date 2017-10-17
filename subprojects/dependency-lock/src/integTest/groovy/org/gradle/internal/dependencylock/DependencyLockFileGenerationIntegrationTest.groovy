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

class DependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: 'dependency-lock'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
        """
    }

    def "can generate lock file for all supported dynamic version formats"() {
        given:
        mavenRepo.module('other', 'dep', '0.1').publish()
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << """
            configurations {
                myConf
            }
            
            dependencies {
                myConf 'other:dep:0.1'
                myConf 'foo:bar:1.+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """

        when:
        succeeds(DependencyLockPlugin.GENERATE_LOCK_FILE_TASK_NAME)

        then:
        file('dependencies.lock').text == '[{"myConf":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"},{"requestedVersion":"latest.release","coordinates":"my:prod","lockedVersion":"3.2.1"},{"requestedVersion":"[1.0,2.0]","coordinates":"dep:range","lockedVersion":"1.7.1"}]}]'
    }

    def "can generate lock file all configurations"() {
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
        file('dependencies.lock').text == '[{"compile":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"compileClasspath":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"default":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"runtime":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"runtimeClasspath":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"testCompile":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"testCompileClasspath":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"testRuntime":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]},{"testRuntimeClasspath":[{"requestedVersion":"1.+","coordinates":"foo:bar","lockedVersion":"1.3"}]}]'
    }
}
