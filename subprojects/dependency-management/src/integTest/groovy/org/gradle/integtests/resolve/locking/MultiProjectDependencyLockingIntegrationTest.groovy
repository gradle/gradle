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

class MultiProjectDependencyLockingIntegrationTest  extends AbstractDependencyResolutionTest {

    def firstLockFileFixture = new LockfileFixture(testDirectory: testDirectory.file('first'))

    def setup() {
        settingsFile << """
rootProject.name = 'multiDepLock'
include 'first', 'second'
"""
    }

    def 'does not apply lock defined in a dependent project'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
allprojects {
    apply plugin: 'java-library'
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':first') {
    configurations.all {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        api 'org:foo:[1.0,2.0)'
    }
}

project(':second') {
    dependencies {
        implementation project(':first')
    }
}
"""
        firstLockFileFixture.createLockfile('compileClasspath', ['org:foo:1.0'], false)

        when:
        succeeds ':first:dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.0')

        when:
        succeeds ':second:dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.1')
    }

    def 'does apply lock to transitive dependencies from dependent project'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
allprojects {
    apply plugin: 'java-library'
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':first') {
    configurations.all {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        implementation project(':second')
    }
}

project(':second') {
    dependencies {
        api 'org:foo:[1.0,2.0)'
    }
}
"""
        firstLockFileFixture.createLockfile('compileClasspath', ['org:foo:1.0'], false)

        when:
        succeeds ':first:dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.0')

        when:
        succeeds ':second:dependencyInsight', '--configuration', 'compileClasspath', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.1')
    }

    def 'creates a lock file including transitive dependencies of dependent project'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
allprojects {
    apply plugin: 'java-library'
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':first') {
    configurations.all {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencies {
        implementation project(':second')
    }
}

project(':second') {
    dependencies {
        api 'org:foo:[1.0,2.0)'
    }
}
"""
        when:
        succeeds ':first:dependencies', '--configuration', 'compileClasspath', '--write-locks'

        then:
        outputContains('Persisted dependency lock state for project \':first\'')
        firstLockFileFixture.verifyLockfile('compileClasspath', ['org:foo:1.1'])
    }
}
