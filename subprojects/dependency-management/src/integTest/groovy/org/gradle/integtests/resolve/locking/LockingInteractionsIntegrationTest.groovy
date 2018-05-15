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
import spock.lang.Unroll

class LockingInteractionsIntegrationTest extends AbstractDependencyResolutionTest {

    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)

    def setup() {
        settingsFile << "rootProject.name = 'locking-interactions'"
    }

    def 'locking constraints do not bring back excluded modules'() {
        def foo = mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').dependsOn(foo).publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf('org:bar:1.+') {
        exclude module: 'foo'
    }
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:bar:1.0'])

        when:
        succeeds 'dependencies'

        then:
        outputContains('org:bar:1.0')
        outputDoesNotContain('foo')

    }

    def 'does not lock dependencies missing a version'() {
        def flatRepo = testDirectory.file('repo')
        flatRepo.createFile('my-dep-1.0.jar')

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    flatDir {
        dirs 'repo'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf name: 'my-dep-1.0'
}
"""
        when:
        succeeds 'dependencies', '--write-locks'

        then:
        outputContains('my-dep-1.0')
        lockfileFixture.verifyLockfile('lockedConf', [])
    }

    @Unroll
    def "can lock when using latest.#level"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.1-SNAPSHOT').withNonUniqueSnapshots().publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:latest.$level'
}
"""
        def version = '1.0'
        if (level == 'integration') {
            version += '-SNAPSHOT'
        }
        lockfileFixture.createLockfile('lockedConf',["org:bar:$version".toString()])

        when:
        succeeds 'dependencies'

        then:
        outputContains("org:bar:$resolvedVersion")

        where:
        level           | resolvedVersion
        'release'       | '1.0'
        'integration'   | '1.0-SNAPSHOT'
    }

    @Ignore
    def "can lock a unique SNAPSHOT module"() {
        def uniqueSnapshotModule = mavenRepo.module('org', 'unique', '1.0-SNAPSHOT').publish()

        buildFile << """
group "org"
version "1.0"
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations {
    lockedConf {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    lockedConf 'org:unique:1.0-SNAPSHOT'
}

task resolve(type: Copy) {
    from configurations.lockedConf
    into 'libs'
}
"""

        when: // Resolve and write locks
        succeeds 'resolve', '--write-locks'

        then:
        lockfileFixture.verifyLockfile("lockedConf", ["org:unique:1.0-SNAPSHOT:20100101.120001-1"])
        file('libs').assertHasDescendants("unique-1.0-20100101.120001-1.jar")

        when: // Publish new snapshot, and resolve again with the lock file
        uniqueSnapshotModule.publishWithChangedContent()

        and:
        succeeds 'resolve'

        then:
        file('libs').assertHasDescendants("unique-1.0-20100101.120001-1.jar")
    }
}
