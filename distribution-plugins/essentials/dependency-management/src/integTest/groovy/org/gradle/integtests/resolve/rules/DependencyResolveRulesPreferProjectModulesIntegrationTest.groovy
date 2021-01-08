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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.resolve.AbstractProjectDependencyConflictResolutionIntegrationSpec
import org.gradle.internal.build.BuildState

class DependencyResolveRulesPreferProjectModulesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        mavenRepo.module("myorg", "ModuleC", "2.0").publish()
        mavenRepo.module("myorg", "ModuleD", "2.0").publish()
        mavenRepo.module("myorg", "ModuleB", '1.0').dependsOn("myorg", "ModuleC", "2.0").publish()
    }

    def "preferProjectModules() only influence dependency declarations in the subproject it is used in"() {
        when:
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
                ${AbstractProjectDependencyConflictResolutionIntegrationSpec.check('ModuleC', 'projectId("ModuleC")', 'conf', 'projectId("ModuleC")')}
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

                // 'Subproject_with_preferProjectModules' config DOES NOT influence this dependency
                // and hence the higher version is picked from repo
                ${AbstractProjectDependencyConflictResolutionIntegrationSpec.check('ModuleC', 'projectId("ModuleC")','conf', 'moduleId("myorg", "ModuleC", "2.0")')}
            }
            ${AbstractProjectDependencyConflictResolutionIntegrationSpec.checkHelper(buildId, projectPath)}
"""
        then:
        succeeds('Subproject_with_preferProjectModules:checkModuleC_conf')
        succeeds('Subproject_without_preferProjectModules:checkModuleC_conf')
    }

    def "preferProjectModules() does not propagate to extending configurations"() {
        when:
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

                ${AbstractProjectDependencyConflictResolutionIntegrationSpec.check('ModuleC', 'projectId("ModuleC")', 'baseConf', 'projectId("ModuleC")')}
                // 'preferProjectModules()' is not inherited from 'baseConf'
                // and hence the higher version is picked from repo
                ${AbstractProjectDependencyConflictResolutionIntegrationSpec.check('ModuleC', 'projectId("ModuleC")', 'conf', 'moduleId("myorg", "ModuleC", "2.0")')}
            }
            ${AbstractProjectDependencyConflictResolutionIntegrationSpec.checkHelper(buildId, projectPath)}
"""
        then:
        succeeds('ProjectA:checkModuleC_conf')
        succeeds('ProjectA:checkModuleC_baseConf')
    }

    String getBuildId() {
        "((${ProjectInternal.name}) project).getServices().get(${BuildState.name}.class).getBuildIdentifier()"
    }

    String getProjectPath() {
        "':' + projectName"
    }
}
