/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.strict

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

/**
 * Regression tests for graphs where strict versions were not applied to every node they apply to.
 */
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class StrictVersionsIssuesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "a request in a cycle below a strict constraint of the root is ignored"() {
        given:
        repository {
            // The cycle pinned:3 -> middle:1 -> back:1 -> pinned:3 is only reached through pinned:3, which the root
            // strictly constrains. bypass:2 also reaches back:1 for a moment, and leaves the graph once upgraded is
            // upgraded to 3
            id("org:back:1") {
                dependsOn("org:pinned:3")
            }
            id("org:bypass:2") {
                dependsOn("org:upgraded:3")
                dependsOn("org:back:1")
            }
            id("org:middle:1") {
                dependsOn("org:back:1")
            }
            id("org:pinned:3") {
                dependsOn("org:middle:1")
            }
            id("org:upgraded:2") {
                dependsOn("org:bypass:2")
            }
            id("org:upgraded:3")
        }

        buildFile << """
            configurations {
                // Sorted before 'conf', so the strict constraint is listed before the dependencies
                create("aStrict")
                named("conf") {
                    extendsFrom(aStrict)
                }
            }
            dependencies {
                constraints {
                    aStrict("org:pinned") {
                        version {
                            strictly("3")
                        }
                    }
                }
                conf("org:pinned:3")
                conf("org:upgraded:2")
            }
        """

        when:
        repositoryInteractions {
            allowAll()
        }
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:pinned:{strictly 3}", "org:pinned:3")
                module("org:pinned:3") {
                    byConstraint()
                    byAncestor()
                    module("org:middle:1") {
                        module("org:back:1") {
                            module("org:pinned:3")
                        }
                    }
                }
                edge("org:upgraded:2", "org:upgraded:3") {
                    byConflictResolution("between versions 3 and 2")
                }
            }
        }
    }

    def "a strict version of a transitive dependency applies to a cycle once the other path to the cycle is evicted"() {
        given:
        repository {
            id("org:leaf:1")
            id("org:leaf:2")
            // cycle-a <-> cycle-b is a cycle, and cycle-a asks for leaf:2
            id("org:cycle-a:1") {
                dependsOn("org:leaf:2")
                dependsOn("org:cycle-b:1")
            }
            id("org:cycle-b:1") {
                dependsOn("org:cycle-a:1")
            }
            // declarer leads into the cycle and strictly requires leaf:1
            id("org:declarer:1") {
                dependsOn("org:cycle-a:1")
                dependsOn(group: "org", artifact: "leaf", version: "1", strictly: "1")
            }
            // bypass:1 is a second path into the cycle, which does not go through declarer
            id("org:bypass:1") {
                dependsOn("org:cycle-a:1")
            }
            // bypass is upgraded to 2, which has no dependencies, at the end of a path long enough for bypass:1 to
            // leave the graph after the cycle is closed
            id("org:bypass:2")
            id("org:slow:1") {
                dependsOn("org:slower:1")
            }
            id("org:slower:1") {
                dependsOn("org:slowest:1")
            }
            id("org:slowest:1") {
                dependsOn("org:bypass:2")
            }
        }

        buildFile << """
            dependencies {
                conf("org:declarer:1")
                conf("org:slow:1")
                conf("org:bypass:1")
            }
        """

        when:
        repositoryInteractions {
            allowAll()
        }
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:declarer:1") {
                    module("org:cycle-a:1") {
                        edge("org:leaf:2", "org:leaf:1") {
                            byAncestor()
                        }
                        module("org:cycle-b:1") {
                            module("org:cycle-a:1")
                        }
                    }
                    edge("org:leaf:{strictly 1}", "org:leaf:1")
                }
                module("org:slow:1") {
                    module("org:slower:1") {
                        module("org:slowest:1") {
                            module("org:bypass:2")
                        }
                    }
                }
                edge("org:bypass:1", "org:bypass:2") {
                    byConflictResolution("between versions 2 and 1")
                }
            }
        }
    }

    def "an endorsed platform applies to a library once conflicts remove its other paths"() {
        given:
        repository {
            id("org:x:1")
            id("org:x:2")
            // Shrunk from a Spring Boot application, where Jetty and JUnit reach each other through Pulsar.
            // app endorses platform, so every node below app inherits the strict version 1 of x. requester asks for
            // x:2. early reaches lib and requester through detour:1 and shortcut:1 until upgrader upgrades both to 2
            id("org:app:1") {
                dependsOn(group: "org", artifact: "platform", version: "1", endorseStrictVersions: true)
                dependsOn("org:main:1")
                dependsOn("org:early:1")
            }
            id("org:platform:1") {
                dependsOn(group: "org", artifact: "x", version: "1", strictly: "1")
            }
            id("org:main:1") {
                dependsOn("org:deep:1")
                dependsOn("org:lib:1")
            }
            id("org:deep:1") {
                dependsOn("org:deeper:1")
            }
            id("org:deeper:1") {
                dependsOn("org:upgrader:1")
            }
            id("org:upgrader:1") {
                dependsOn("org:detour:2")
                dependsOn("org:shortcut:2")
            }
            id("org:lib:1") {
                dependsOn("org:requester:1")
            }
            id("org:requester:1") {
                dependsOn("org:x:2")
            }
            id("org:early:1") {
                dependsOn("org:detour:1")
                dependsOn("org:shortcut:1")
            }
            id("org:detour:1") {
                dependsOn("org:via:1")
            }
            id("org:detour:2")
            id("org:via:1") {
                dependsOn("org:lib:1")
            }
            id("org:shortcut:1") {
                dependsOn("org:requester:1")
            }
            id("org:shortcut:2")
        }

        buildFile << """
            dependencies {
                conf("org:app:1")
            }
        """

        when:
        repositoryInteractions {
            allowAll()
        }
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:app:1") {
                    module("org:platform:1") {
                        edge("org:x:{strictly 1}", "org:x:1")
                    }
                    module("org:main:1") {
                        module("org:deep:1") {
                            module("org:deeper:1") {
                                module("org:upgrader:1") {
                                    module("org:detour:2") {
                                        byConflictResolution("between versions 2 and 1")
                                    }
                                    module("org:shortcut:2") {
                                        byConflictResolution("between versions 2 and 1")
                                    }
                                }
                            }
                        }
                        module("org:lib:1") {
                            module("org:requester:1") {
                                edge("org:x:2", "org:x:1") {
                                    byAncestor()
                                }
                            }
                        }
                    }
                    module("org:early:1") {
                        edge("org:detour:1", "org:detour:2")
                        edge("org:shortcut:1", "org:shortcut:2")
                    }
                }
            }
        }
    }

    def "an endorsed platform applies to a cycle once a conflict removes one of its entries"() {
        given:
        repository {
            id("org:x:1")
            id("org:x:2")
            // Shrunk from a Spring Cloud application on AWS, where every module endorses the platform of its
            // organization. app endorses platform, so every node below app inherits the strict version 1 of x, including the cycle
            // of first and second, where first asks for x:2. bridge:1 enters the cycle through side until upgrader
            // brings bridge:2, which does not
            id("org:app:1") {
                dependsOn(group: "org", artifact: "platform", version: "1", endorseStrictVersions: true)
                dependsOn("org:entry:1")
                dependsOn("org:slow:1")
            }
            id("org:platform:1") {
                dependsOn(group: "org", artifact: "x", version: "1", strictly: "1")
            }
            id("org:entry:1") {
                dependsOn("org:first:1")
                dependsOn("org:bridge:1")
            }
            id("org:first:1") {
                dependsOn("org:second:1")
                dependsOn("org:x:2")
            }
            id("org:second:1") {
                dependsOn("org:first:1")
            }
            id("org:bridge:1") {
                dependsOn("org:side:1")
            }
            id("org:bridge:2")
            id("org:side:1") {
                dependsOn("org:second:1")
            }
            id("org:slow:1") {
                dependsOn("org:slower:1")
            }
            id("org:slower:1") {
                dependsOn("org:upgrader:1")
            }
            id("org:upgrader:1") {
                dependsOn("org:bridge:2")
            }
        }

        buildFile << """
            dependencies {
                conf("org:app:1")
            }
        """

        when:
        repositoryInteractions {
            allowAll()
        }
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:app:1") {
                    module("org:platform:1") {
                        edge("org:x:{strictly 1}", "org:x:1")
                    }
                    module("org:entry:1") {
                        module("org:first:1") {
                            module("org:second:1") {
                                module("org:first:1")
                            }
                            edge("org:x:2", "org:x:1") {
                                byAncestor()
                            }
                        }
                        edge("org:bridge:1", "org:bridge:2")
                    }
                    module("org:slow:1") {
                        module("org:slower:1") {
                            module("org:upgrader:1") {
                                module("org:bridge:2") {
                                    byConflictResolution("between versions 2 and 1")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
