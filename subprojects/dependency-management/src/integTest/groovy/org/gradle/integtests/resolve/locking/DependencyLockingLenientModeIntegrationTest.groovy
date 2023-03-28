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

class DependencyLockingLenientModeIntegrationTest extends AbstractLockingIntegrationTest {
    @Override
    LockMode lockMode() {
        return LockMode.LENIENT
    }

    def 'does not fail when lock file conflicts with declared strict constraint (initial unique: #unique)'() {
        given:
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    lockedConf 'org:foo:1.+'
    lockedConf('org:foo') {
        version { strictly '1.1' }
    }
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                edge("org:foo:1.+", "org:foo:1.1") {
                    byConflictResolution("between versions 1.0 and 1.1")
                    byConstraint("dependency was locked to version '1.0' (update/lenient mode)")
                }
                edge("org:foo:{strictly 1.1}", "org:foo:1.1")
                constraint("org:foo:1.0", "org:foo:1.1")
            }
        }

        where:
        unique << [true, false]
    }

    def 'does not fail when lock file conflicts with declared version constraint (initial unique: #unique)'() {
        given:
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    lockedConf 'org:foo:1.+'
    lockedConf('org:foo:1.1')
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                edge("org:foo:1.+", "org:foo:1.1") {
                    byConstraint("dependency was locked to version '1.0' (update/lenient mode)")
                    byConflictResolution("between versions 1.0 and 1.1")
                }
                module("org:foo:1.1")
                constraint("org:foo:1.0", "org:foo:1.1")
            }
        }

        where:
        unique << [true, false]
    }

    def 'does not fail when lock file contains entry that is not in resolution result (initial unique: #unique)'() {

        given:
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.0', 'org:baz:1.0'], unique)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                edge("org:foo:1.+", "org:foo:1.0")
                constraint("org:foo:1.0", "org:foo:1.0") {
                    byConstraint("dependency was locked to version '1.0' (update/lenient mode)")
                }
            }
        }

        where:
        unique << [true, false]
    }

    def 'does not fail when lock file does not contain entry for module in resolution result (initial unique: #unique)'() {
        given:
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                edge("org:foo:1.+", "org:foo:1.0")
                edge("org:bar:1.+", "org:bar:1.0")
                constraint("org:foo:1.0", "org:foo:1.0") {
                    byConstraint("dependency was locked to version '1.0' (update/lenient mode)")
                }
            }
        }

        where:
        unique << [true, false]
    }

    def 'does not fail when resolution result is empty and lock file contains entries (initial unique: #unique)'() {
        given:
        mavenRepo.module('org', 'foo', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
"""
        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectDefaultConfiguration('runtime')
        resolve.expectGraph {
            root(":", ":depLock:") {
                // Empty result
            }
        }

        where:
        unique << [true, false]
    }

    def 'dependency report passes without failed dependencies using out-of-date lock file'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    constraints {
        lockedConf('org:foo:1.1')
    }
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'])

        when:
        run 'dependencies'

        then:
        outputContains """lockedConf
+--- org:foo:1.+ -> 1.1
+--- org:foo:1.1 (c)
\\--- org:foo:1.0 -> 1.1 (c)"""
    }

    def 'dependency report passes without FAILED dependencies for all out lock issues'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
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
    constraints {
        lockedConf('org:foo:1.1')
    }
    lockedConf 'org:foo:[1.0, 1.1]'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:bar:1.0', 'org:foo:1.0'])

        when:
        run 'dependencies'

        then:
        outputContains """lockedConf
+--- org:foo:[1.0, 1.1] -> 1.1
+--- org:foo:1.1 (c)
\\--- org:foo:1.0 -> 1.1 (c)"""
    }
}
