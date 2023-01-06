/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.util.Path

/**
 * This tests the following scenario with different version settings:
 *
 * ProjectA ─(project dependency)─> myorg.ModuleC
 *  |
 *  └> myorg.ModuleB(1.0) ────────> myorg.ModuleC($versionExternal)
 *
 * Of myorg.ModuleC...
 * - ...all referenced versions exist in a repository as 'module' (see test setup)
 * - ...one version exists as 'project' in the build (either through multi-project or composite)
 *
 * This abstract specification leaves out the concrete implementation of setting up the project structure.
 * Subclasses can do that via multi-project or composite build.
 */
abstract class AbstractProjectDependencyConflictResolutionIntegrationSpec extends AbstractIntegrationSpec {

    abstract String getIncludeMechanism();

    abstract String getBuildId();

    abstract String getProjectPath();

    abstract String dependsOnMechanism(String projectName, String taskName);

    abstract String declareDependency(String moduleName, String moduleVersion);

    abstract String declaredDependencyId(String moduleName, String moduleVersion);

    abstract void moduleDefinition(String name, String definition);

    /**
     * Indicates that external modules are automatically substituted by corresponding projects (independent of versions).
     * This is the behavior in composite builds.
     */
    abstract boolean isAutoDependencySubstitution();

    def "project (#projectDep) vs external (#transitiveDep) resolves to (#winner), when preferProjectModules=#preferProjectModules and depSubstitution=#depSubstitution"() {
        given:
        //required for composite builds
        buildTestFixture.withBuildInSubDir()

        //publish all the versions of ModuleC that we might need
        mavenRepo.module("myorg", "ModuleC", transitiveDep).publish()
        mavenRepo.module("myorg", "ModuleC", projectDep).publish()

        //publish ModuleB:1.0 and declare its dependency to ModuleC:$versionExternal
        mavenRepo.module("myorg", "ModuleB", '1.0').dependsOn("myorg", "ModuleC", transitiveDep).publish()

        //setup the project structure
        settingsFile << "$includeMechanism 'ModuleC'\n$includeMechanism 'ProjectA'\n"

        when:

        def preferProjectModulesOption = ''
        def depSubstitutionOption = '';

        if (preferProjectModules) {
            preferProjectModulesOption = 'preferProjectModules()'
        }
        if (!autoDependencySubstitution) { //only interpret depSubstitution in scenario without auto substitution (multi-project builds)
            depSubstitutionOption = depSubstitution;
        } else {
            winner = 'projectId("ModuleC")'
        }

        buildFile << """
            task check {
                dependsOn ${dependsOnMechanism('ProjectA', 'checkModuleC_conf')}
            }
            ${checkHelper(buildId, projectPath)}
"""
        moduleDefinition('ModuleC', """
            group "myorg"
            version = $projectDep

            configurations { conf }
            configurations.create("default").extendsFrom(configurations.conf)
""")

        moduleDefinition('ProjectA', """
            repositories { maven { url "${mavenRepo.uri}" } }

            configurations { conf }

            configurations.conf.resolutionStrategy {
                $preferProjectModulesOption
                dependencySubstitution { $depSubstitutionOption }
            }

            dependencies {
                conf "myorg:ModuleB:1.0"
                conf ${declareDependency('ModuleC', projectDep)}
            }

            ${check('ModuleC', declaredDependencyId('ModuleC', projectDep), 'conf', winner)}
""")

        then:
        succeeds('check')

        where:
        projectDep | transitiveDep | winner                                | preferProjectModules | depSubstitution
//        "1.9"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.0")' | false                | ''
//        "2.0"      | "2.0"         | 'projectId("ModuleC")'                | false                | ''
        "2.1"      | "2.0"         | 'projectId("ModuleC")'                | false                | ''
        "1.9"      | "2.0"         | 'projectId("ModuleC")'                | true                 | ''
        "2.0"      | "2.0"         | 'projectId("ModuleC")'                | true                 | ''
        "2.1"      | "2.0"         | 'projectId("ModuleC")'                | true                 | ''
        "1.9"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.0")' | false                | "substitute project(':ModuleC') using module('myorg:ModuleC:1.9')"
        "2.0"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.0")' | false                | "substitute project(':ModuleC') using module('myorg:ModuleC:2.0')"
        "2.1"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.1")' | false                | "substitute project(':ModuleC') using module('myorg:ModuleC:2.1')"
        "1.9"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.0")' | true                 | "substitute project(':ModuleC') using module('myorg:ModuleC:1.9')"
        "2.0"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.0")' | true                 | "substitute project(':ModuleC') using module('myorg:ModuleC:2.0')"
        "2.1"      | "2.0"         | 'moduleId("myorg", "ModuleC", "2.1")' | true                 | "substitute project(':ModuleC') using module('myorg:ModuleC:2.1')"
        "1.9"      | "2.0"         | 'projectId("ModuleC")'                | false                | "substitute module('myorg:ModuleC') using project(':ModuleC')"
        "2.0"      | "2.0"         | 'projectId("ModuleC")'                | false                | "substitute module('myorg:ModuleC') using project(':ModuleC')"
        "2.1"      | "2.0"         | 'projectId("ModuleC")'                | false                | "substitute module('myorg:ModuleC') using project(':ModuleC')"
    }

    static String check(String moduleName, String declaredDependencyId, String confName, String winner) { """
        task check${moduleName}_${confName} {
            def result = configurations.${confName}.incoming.resolutionResult.rootComponent
            def declared = $declaredDependencyId
            def expected = $winner
            doLast {
                def deps = result.get().dependencies as List
                def projectDependency = deps.find {
                    it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(declared)
                }

                assert projectDependency && projectDependency.selected.componentId == expected
            }
        }
"""
    }

    static String checkHelper(String buildId, String projectPath) { """
        def moduleId(String group, String name, String version) {
            def mid = org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId(group, name)
            return org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(mid, version)
        }

        def projectId(String projectName) {
            def buildId = $buildId
            def projectPath = $projectPath
            return project.services.get(${BuildStateRegistry.name}).getBuild(buildId).projects.getProject(${Path.name}.path(projectPath)).componentIdentifier
        }
"""
    }

}
