/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.dsl.LockMode

class DependencyLockingStrictModeIntegrationTest extends AbstractValidatingLockingIntegrationTest {

    @Override
    LockMode lockMode() {
        LockMode.STRICT
    }

    def 'fails without lock file present and does not create one'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        when:
        fails 'dependencies'

        then:
        failureHasCause("Locking strict mode: Configuration ':lockedConf' is locked but does not have lock state.")
        failure.assertHasResolution("To create the lock state, run a task that performs dependency resolution and add '--write-locks' to the command line.")
        failure.assertHasResolution("For more information on generating lock state")
        lockfileFixture.expectLockStateMissing('unlockedConf')
    }

    def 'fails if update done without lockfile present'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    lockedConf 'org:bar:[1.0,2.0)'
}
"""

        when:
        fails 'dependencies', '--update-locks', 'org:foo'

        then:
        failureHasCause("Locking strict mode: Configuration ':lockedConf' is locked but does not have lock state.")
        lockfileFixture.expectLockStateMissing('unlockedConf')
    }

    def 'ignores not locked configurations'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    unlockedConf
}

dependencies {
    unlockedConf 'org:foo:1.+'
}
"""

        expect:
        succeeds 'dependencies'
    }

}
