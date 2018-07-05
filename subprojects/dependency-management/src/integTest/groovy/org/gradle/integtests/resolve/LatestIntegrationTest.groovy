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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Ignore

class LatestIntegrationTest extends AbstractIntegrationSpec {
    private final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        settingsFile << "rootProject.name = 'testLatest'"
        resolve.prepare()
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
        """
    }

    void 'latest.release does not select snapshot'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.1-SNAPSHOT').publish()
        mavenRepo.module("org", "foo", '2.0-SNAPSHOT').publish()

        buildFile << """
            dependencies {
                conf 'org:foo:latest.release'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":testLatest:") {
                edge("org:foo:latest.release","org:foo:1.1")
            }
        }
    }

    void 'latest.release and range constraint excluding highest still selects it'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "foo", '2.0').publish()

        buildFile << """
            dependencies {
                constraints {
                    conf 'org:foo:[1.0,2.0)'
                }
                conf 'org:foo:latest.release'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":testLatest:") {
                edge("org:foo:latest.release","org:foo:2.0")
                edge("org:foo:[1.0,2.0)","org:foo:2.0")
            }
        }
    }

    void 'latest.release and prefer constraint selects latest'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "foo", '2.0').publish()

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                        version {
                            prefer '1.2'
                        }
                    }
                }
                conf 'org:foo:latest.release'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":testLatest:") {
                edge("org:foo:latest.release","org:foo:2.0")
                edge("org:foo:1.2","org:foo:2.0")
            }
        }
    }

    @Ignore('This ends up selecting a snapshot while we have a latest.release')
    void 'latest.release and prefer constraint with wrong status selects latest'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '2.0').publish()
        mavenRepo.module("org", "foo", '2.1-SNAPSHOT').withNonUniqueSnapshots().publish()

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                        version {
                            prefer '2.1-SNAPSHOT'
                        }
                    }
                }
                conf 'org:foo:latest.release'
            }
        """

        expect:
        fails 'checkDeps'

//        Build currently passes and resolves:
//        resolve.expectGraph {
//            root(":", ":testLatest:") {
//                edge("org:foo:latest.release","org:foo:2.1-SNAPSHOT")
//                edge("org:foo:2.1-SNAPSHOT","org:foo:2.1-SNAPSHOT")
//            }
//        }
    }

    void 'latest.release and strict constraint selects strict'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "foo", '2.0').publish()

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                        version {
                            strictly '1.2'
                        }
                    }
                }
                conf 'org:foo:latest.release'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":testLatest:") {
                edge("org:foo:latest.release","org:foo:1.2")
                edge("org:foo:1.2","org:foo:1.2")
            }
        }
    }

    @Ignore('This ends up selecting a snapshot while we have a latest.release')
    void 'latest.release and strict constraint with wrong status fails to resolve'() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module("org", "foo", '2.0').publish()

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                        version {
                            strictly '1.2-SNAPSHOT'
                        }
                    }
                }
                conf 'org:foo:latest.release'
            }
        """

        expect:
        fails 'checkDeps'

//        Build currently passes and resolves:
//        resolve.expectGraph {
//            root(":", ":testLatest:") {
//                edge("org:foo:latest.release","org:foo:1.2-SNAPSHOT")
//                edge("org:foo:1.2-SNAPSHOT","org:foo:1.2-SNAPSHOT")
//            }
//        }
    }
}
