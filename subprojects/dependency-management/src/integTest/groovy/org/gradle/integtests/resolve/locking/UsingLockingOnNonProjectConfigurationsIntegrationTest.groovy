/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.locking

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Ignore

class UsingLockingOnNonProjectConfigurationsIntegrationTest extends AbstractDependencyResolutionTest {

    @Ignore('classpath configuration re-creates a resolution strategy, loosing the locking info')
    def 'locks build script classpath configuration'() {
        given:
        mavenRepo.module('org.foo', 'foo-plugin', '1.0').publish()
        mavenRepo.module('org.foo', 'foo-plugin', '1.1').publish()

        settingsFile << """
rootProject.name = 'foo'
"""
        buildFile << """
buildscript {
    repositories {
        maven {
            url = '$mavenRepo.uri'
        }
    }
    configurations {
        classpath {
            resolutionStrategy.activateDependencyLocking()
            resolutionStrategy.failOnVersionConflict()
        }
    }
    dependencies {
        classpath 'org.foo:foo-plugin:1.0'
        classpath 'org.foo:foo-plugin:1.1'
    }
}
"""
        def lockFile = new LockfileFixture(testDirectory: testDirectory.file('gradle'))
        lockFile.createLockfile('classpath', ['org.foo:foo-plugin:1.0'])

        when:
        succeeds 'buildEnvironment'

        then:
        outputContains('org.foo:foo-plugin:1.0')
        outputDoesNotContain('org.foo:foo-plugin:1.1')
    }
}
