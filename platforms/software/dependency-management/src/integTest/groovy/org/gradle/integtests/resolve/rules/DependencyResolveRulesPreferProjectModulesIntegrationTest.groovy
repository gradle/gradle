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

package org.gradle.integtests.resolve.rules


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class DependencyResolveRulesPreferProjectModulesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        mavenRepo.module("myorg", "ModuleC", "2.0").publish()
        mavenRepo.module("myorg", "ModuleD", "2.0").publish()
        mavenRepo.module("myorg", "ModuleB", '1.0').dependsOn("myorg", "ModuleC", "2.0").publish()
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve.prepare()
    }

    def "preferProjectModules() only influence dependency declarations in the subproject it is used in"() {
        createDirs("ModuleC", "Subproject_with_preferProjectModules", "Subproject_without_preferProjectModules")
        settingsFile << 'include "ModuleC", "Subproject_with_preferProjectModules", "Subproject_without_preferProjectModules"'

        buildFile << """
            project(":ModuleC") {
                group "myorg"
                version = "1.0"

                configurations { conf }
                configurations.create("default").extendsFrom(configurations.conf)
            }

            project(":Subproject_with_preferProjectModules") {
                repositories { maven { url "${mavenRepo.uri}" } }

                configurations { conf }
                configurations.create("default").extendsFrom(configurations.conf)

                configurations.conf.resolutionStrategy {
                    preferProjectModules()
                }

                dependencies {
                    conf "myorg:ModuleB:1.0"
                    conf project(":ModuleC")
                }
            }

            project(":Subproject_without_preferProjectModules") {
                repositories { maven { url "${mavenRepo.uri}" } }

                configurations { conf }
                configurations.create("default").extendsFrom(configurations.conf)

                dependencies {
                    conf project(":Subproject_with_preferProjectModules")
                    conf "myorg:ModuleB:1.0"
                    conf project(":ModuleC")
                }
            }
"""
        when:
        succeeds('Subproject_with_preferProjectModules:checkDeps')

        then:
        resolve.expectGraph {
            root(":Subproject_with_preferProjectModules", "test:Subproject_with_preferProjectModules:") {
                module("myorg:ModuleB:1.0") {
                    // Prefers project, regardless of version
                    edge("myorg:ModuleC:2.0", ":ModuleC", "myorg:ModuleC:1.0") {
                        byConflictResolution("between versions 1.0 and 2.0")
                    }
                }
                project(":ModuleC", "myorg:ModuleC:1.0") {
                    noArtifacts()
                }
            }
        }

        when:
        succeeds('Subproject_without_preferProjectModules:checkDeps')

        then:
        resolve.expectGraph {
            root(":Subproject_without_preferProjectModules", "test:Subproject_without_preferProjectModules:") {
                project(":Subproject_with_preferProjectModules", "test:Subproject_with_preferProjectModules:") {
                    noArtifacts()
                    module("myorg:ModuleB:1.0") {
                        module("myorg:ModuleC:2.0") {
                            byConflictResolution("between versions 1.0 and 2.0")
                        }
                    }
                    // 'Subproject_with_preferProjectModules' config DOES NOT influence this dependency
                    // and hence the higher version is picked from repo
                    edge("project :ModuleC", "myorg:ModuleC:2.0")
                }
                module("myorg:ModuleB:1.0")
                edge("project :ModuleC", "myorg:ModuleC:2.0")
            }
        }
    }

    def "preferProjectModules() does not propagate to extending configurations"() {
        createDirs("ModuleC", "ProjectA")
        settingsFile << 'include "ModuleC", "ProjectA"'

        buildFile << """
            project(":ModuleC") {
                group "myorg"
                version = "1.0"

                configurations {
                    baseConf
                    conf.extendsFrom(baseConf)
                }
                configurations.create("default").extendsFrom(configurations.baseConf)
            }

            project(":ProjectA") {
                repositories { maven { url "${mavenRepo.uri}" } }

                configurations {
                    baseConf
                    conf.extendsFrom(baseConf)
                }
                configurations.create("default").extendsFrom(configurations.baseConf)

                configurations.baseConf.resolutionStrategy {
                    preferProjectModules()
                }

                dependencies {
                    conf "myorg:ModuleB:1.0"
                    conf project(":ModuleC")

                    baseConf "myorg:ModuleB:1.0"
                    baseConf project(":ModuleC")
                }
            }
"""

        when:
        succeeds('ProjectA:checkDeps')

        then:
        resolve.expectGraph {
            root(":ProjectA", "test:ProjectA:") {
                module("myorg:ModuleB:1.0") {
                    module("myorg:ModuleC:2.0") {
                        byConflictResolution("between versions 1.0 and 2.0")
                    }
                }
                // 'preferProjectModules()' is not inherited from 'baseConf'
                // and hence the higher version is picked from repo
                edge("project :ModuleC", "myorg:ModuleC:2.0")
            }
        }

        when:
        resolve.prepare('baseConf')
        succeeds('ProjectA:checkDeps')

        then:
        resolve.expectGraph {
            root(":ProjectA", "test:ProjectA:") {
                module("myorg:ModuleB:1.0") {
                    edge("myorg:ModuleC:2.0", ":ModuleC", "myorg:ModuleC:1.0") {
                        byConflictResolution("between versions 1.0 and 2.0")
                    }
                }
                project("project :ModuleC", "myorg:ModuleC:1.0") {
                    noArtifacts()
                }
            }
        }
    }
}
