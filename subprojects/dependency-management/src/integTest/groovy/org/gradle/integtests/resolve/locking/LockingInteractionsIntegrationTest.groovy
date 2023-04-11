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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class LockingInteractionsIntegrationTest extends AbstractHttpDependencyResolutionTest {

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

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0'], false)

        when:
        succeeds 'dependencies'

        then:
        outputContains('org:bar')
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
        lockfileFixture.createLockfile('lockedConf', ["org:bar:$version".toString()], false)

        when:
        succeeds 'dependencies'

        then:
        outputContains("org:bar:latest.${level} -> $resolvedVersion")

        where:
        level         | resolvedVersion
        'release'     | '1.0'
        'integration' | '1.0-SNAPSHOT'
    }

    def "can write lock when using #version"() {
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'bar', '2.0').publish()
        mavenRepo.module('org', 'bar', '2.1-SNAPSHOT').publish()
        mavenRepo.module('org', 'bar', '2.1').publish()
        mavenRepo.module('org', 'bar', '2.2-SNAPSHOT').publish()

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
    lockedConf 'org:bar:$version'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        outputContains("org:bar:$version -> $expectedVersion")

        and:
        lockfileFixture.verifyLockfile('lockedConf', ["org:bar:$expectedVersion"])

        where:
        version              | expectedVersion
        '[1.0, 2.0)'         | '1.1'
        '1.+'                | '1.1'
        '[1.0,)'             | '2.2-SNAPSHOT'
        '+'                  | '2.2-SNAPSHOT'
        'latest.release'     | '2.1'
        'latest.integration' | '2.2-SNAPSHOT'

    }

    def 'kind of locks snapshots but warns about it'() {
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()

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
    lockedConf 'org:bar:1.0-SNAPSHOT'
}
"""

        when:
        succeeds 'dependencies', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0-SNAPSHOT'])
        outputContains('Dependency lock state for configuration \':lockedConf\' contains changing modules: [org:bar:1.0-SNAPSHOT]. This means that dependencies content may still change over time.')

        when:
        mavenRepo.module('org', 'bar', '1.0-SNAPSHOT').publish()

        then:
        succeeds 'dependencies'
    }

    def "can update a single lock entry when using #version"() {
        ['bar', 'baz', 'foo'].each { artifactId ->
            mavenRepo.module('org', artifactId, '1.0').publish()
            mavenRepo.module('org', artifactId, '1.1').publish()
            mavenRepo.module('org', artifactId, '2.0').publish()
            mavenRepo.module('org', artifactId, '2.1').publish()
            mavenRepo.module('org', artifactId, '2.2-SNAPSHOT').withNonUniqueSnapshots().publish()
        }

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
    lockedConf 'org:bar:$version'
    lockedConf 'org:baz:1.0' // Ensure the fact that '1.0' is from a lock isn't masked by a real dependency
    lockedConf 'org:baz:$version'
    lockedConf 'org:foo:$version'
}
"""
        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:baz:1.0', 'org:foo:1.0'])

        when:
        succeeds 'dependencies', '--update-locks', 'org:foo'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:bar:1.0', 'org:baz:1.0', "org:foo:$expectedVersion"])

        where:
        version              | expectedVersion
        '[1.0, 2.0)'         | '1.1'
        '1.+'                | '1.1'
        '[1.0,)'             | '2.2-SNAPSHOT'
        '+'                  | '2.2-SNAPSHOT'
        'latest.release'     | '2.1'
        'latest.integration' | '2.2-SNAPSHOT'
    }

    def 'locking works with default dependency action'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

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
    lockedConf {
        defaultDependencies { deps ->
            deps.add(project.dependencies.create('org:foo:1.0'))
        }
    }
}

task copyFiles(type: Copy) {
    from configurations.lockedConf
    into 'build/copied'
}
"""
        when:
        succeeds 'copyFiles', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.0'])

        and:
        succeeds 'copyFiles'

    }

    def "fails when a force overwrites a locked version"() {
        given:
        mavenRepo.module('org', 'test', '1.0').publish()
        mavenRepo.module('org', 'test', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:test:1.1'], false)

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
    lockedConf {
        resolutionStrategy {
            force 'org:test:1.0'
        }
    }
}

dependencies {
    lockedConf 'org:test:[1.0,2.0)'
}

task resolve {
    def files = configurations.lockedConf
    doLast {
        println files.files
    }
}
"""

        when:
        fails 'resolve'

        then:
        failureHasCause("Did not resolve 'org:test:1.1' which has been forced / substituted to a different version: '1.0'")
    }

    def "fails when a substitute overwrites a locked version"() {
        given:
        mavenRepo.module('org', 'test', '1.0').publish()
        mavenRepo.module('org', 'test', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:test:1.1'], false)

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
    lockedConf {
        resolutionStrategy.dependencySubstitution {
            substitute module('org:test') using module('org:test:1.0')
        }
    }
}

dependencies {
    lockedConf 'org:test:[1.0,2.0)'
}

task resolve {
    def files = configurations.lockedConf
    doLast {
        println files.files
    }
}
"""

        when:
        fails 'resolve'

        then:
        failureHasCause("Did not resolve 'org:test:1.1' which has been forced / substituted to a different version: '1.0'")
    }

    def "fails when a useTarget overwrites a locked version"() {
        given:
        mavenRepo.module('org', 'test', '1.0').publish()
        mavenRepo.module('org', 'test', '1.1').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:test:1.1'], false)

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
    lockedConf {
        resolutionStrategy.eachDependency { details ->
            if (details.requested.group == 'org' && details.requested.name == 'test') {
                details.useVersion '1.0'
            }
        }
    }
}

dependencies {
    lockedConf 'org:test:[1.0,2.0)'
}

task resolve {
    def files = configurations.lockedConf
    doLast {
        println files.files
    }
}
"""

        when:
        fails 'resolve'

        then:
        failureHasCause("Did not resolve 'org:test:1.1' which has been forced / substituted to a different version: '1.0'")
    }

    def "ignores the lock entry that matches a composite"() {
        given:
        lockfileFixture.createLockfile('lockedConf', ['org:composite:1.1'], false)

        file("composite/settings.gradle") << """
rootProject.name = 'composite'
"""
        file("composite/build.gradle") << """
apply plugin: 'java'
group = 'org'
version = '1.1'
"""

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
    lockedConf 'org:composite:1.1'
}

task resolve {
    def lockedConf = configurations.lockedConf
    doLast {
        println lockedConf.files
    }
}
"""
        expect:
        succeeds 'resolve', '--include-build', 'composite'
    }

    def "avoids HTTP requests for dynamic version when lock exists"() {
        def foo10 = mavenHttpRepo.module('org', 'foo', '1.0').publish()
        mavenHttpRepo.module('org', 'foo', '1.1').publish()
        mavenHttpRepo.module('org', 'foo', '2.0').publish()
        def bar10 = mavenHttpRepo.module('org', 'bar', '1.0').dependsOn('org', 'foo', '[1.0,2.0)').publish()

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.0'], false)

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    maven {
        name 'repo'
        url '${mavenHttpRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:bar:[1.0,2.0)'
}
"""
        when:
        foo10.pom.expectGet()
        bar10.pom.expectGet()

        then:
        succeeds 'dependencies'
    }

}
