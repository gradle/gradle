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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class MixedDependencyLockingIntegrationTest extends AbstractDependencyResolutionTest {

    def lockfileFixture = new LockfileFixture(testDirectory: testDirectory)

    def setup() {
        settingsFile << "rootProject.name = 'mixedDepLock'"
    }

    def 'can resolve locked and unlocked configurations (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    lockedConf {
        resolutionStrategy.activateDependencyLocking()
    }

    unlockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
    unlockedConf 'org:foo:[1.0,2.0)'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        if (!unique && GradleContextualExecuter.isConfigCache()) {
            executer.expectDocumentedDeprecationWarning("Storing dependency lock state using the legacy format has been deprecated. This is scheduled to be removed in Gradle 10. Since Gradle 6.4, dependency lock state is stored in a single file per project. Your build still stores lock state in a legacy format. Run `./gradlew dependencies --write-locks` to update your build to the new format. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#legacy_dependency_lock_format")
        }
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.0')
        outputContains('Dependency version enforced by Dependency Locking')

        when:
        succeeds 'dependencyInsight', '--configuration', 'unlockedConf', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.1')
        outputDoesNotContain('constraint')

        where:
        unique << [true, false]
    }

    def 'ignores the lockfile of a parent configuration when resolving an unlocked child configuration (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    lockedConf {
        resolutionStrategy.activateDependencyLocking()
    }

    unlockedConf.extendsFrom lockedConf
}

dependencies {
    lockedConf 'org:foo:[1.0,2.0)'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'dependencyInsight', '--configuration', 'unlockedConf', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.1')
        outputDoesNotContain('constraint')

        where:
        unique << [true, false]
    }

    def 'applies the lock file to inherited dependencies (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    unlockedConf

    lockedConf {
        resolutionStrategy.activateDependencyLocking()
        extendsFrom unlockedConf
    }
}

dependencies {
    unlockedConf 'org:foo:[1.0,2.0)'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        if (!unique && GradleContextualExecuter.isConfigCache()) {
            executer.expectDocumentedDeprecationWarning("Storing dependency lock state using the legacy format has been deprecated. This is scheduled to be removed in Gradle 10. Since Gradle 6.4, dependency lock state is stored in a single file per project. Your build still stores lock state in a legacy format. Run `./gradlew dependencies --write-locks` to update your build to the new format. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#legacy_dependency_lock_format")
        }
        succeeds 'dependencyInsight', '--configuration', 'lockedConf', '--dependency', 'foo'

        then:
        outputContains('org:foo:1.0')
        outputContains('Dependency version enforced by Dependency Locking')

        where:
        unique << [true, false]
    }

    def 'writes lock file entries for inherited dependencies'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}
configurations {
    unlockedConf

    lockedConf {
        resolutionStrategy.activateDependencyLocking()
        extendsFrom unlockedConf
    }
}

dependencies {
    unlockedConf 'org:foo:[1.0,2.0)'
}
"""

        when:
        succeeds 'dependencies', '--configuration', 'lockedConf', '--write-locks'

        then:
        lockfileFixture.verifyLockfile('lockedConf', ['org:foo:1.1'])
    }
}
