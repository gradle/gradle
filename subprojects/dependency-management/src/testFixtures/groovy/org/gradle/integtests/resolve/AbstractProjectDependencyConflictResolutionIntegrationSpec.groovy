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
import spock.lang.Unroll

/**
 * This tests the following scenario with different version settings
 * (see also design doc: dependency-management-projectpriority.md):
 *
 * ProjectA ─(project dependency)─> myorg.ModuleC
 *  |
 *  └> myorg.ModuleB(1.0) ────────> myorg.ModuleC($versionExternal)
 *
 * Of myorg.ModuleC...
 * - ...all referenced version exist in a repository as 'module' (see test setup)
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

    @Unroll
    def "project dependency (#versionInProject) vs external dependency (#versionExternal) resolves to winner, when preferProjectModules=#preferProjectModules and forceVersion=#forceVersion and depSubstitution=#depSubstitution"() {
        given:
        //required for composite builds
        buildTestFixture.withBuildInSubDir()

        //publish all the versions of ModuleC that we might need
        mavenRepo.module("myorg", "ModuleC", versionExternal).publish()
        mavenRepo.module("myorg", "ModuleC", versionInProject).publish()
        if (forceVersion != null) {
            mavenRepo.module("myorg", "ModuleC", forceVersion).publish()
        }

        //publish ModuleB:1.0 and declare its dependency to ModuleC:$versionExternal
        mavenRepo.module("myorg", "ModuleB", '1.0').dependsOn("myorg", "ModuleC", versionExternal).publish()

        //setup the project structure
        settingsFile << "$includeMechanism 'ModuleC'\n$includeMechanism 'ProjectA'\n"

        when:

        def preferProjectModulesOption = ''
        def forcedVersionOption = ''
        def depSubstitutionOption = '';

        if (preferProjectModules) {
            preferProjectModulesOption = 'preferProjectModules()'
        }
        if (forceVersion != null) {
            forcedVersionOption = "conf ('myorg:ModuleC:$forceVersion') { force = true }"
        }
        if (!autoDependencySubstitution) { //only interpret depSubstitution in scenario without auto substitution (multi-project builds)
            depSubstitutionOption = depSubstitution;
        }

        buildFile << """
            task check {
                dependsOn ${dependsOnMechanism('ProjectA', 'checkModuleC_conf')}
            }
            ${checkHelper(buildId, projectPath)}
"""
        moduleDefinition('ModuleC', """
            group "myorg"
            version = $versionInProject

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
                conf ${declareDependency('ModuleC', versionInProject)}
                $forcedVersionOption
            }

            ${check('ModuleC', declaredDependencyId('ModuleC', versionInProject),'conf', autoDependencySubstitution ? winnerAutoSubstitution : winner)}
""")

        then:
        succeeds('check')

        where:
                                versionInProject | versionExternal | winner                                | winnerAutoSubstitution | preferProjectModules | forceVersion | depSubstitution
                                // === without preferProjectModules() ===
        /*project <  external*/ "1.9"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.0")' | 'projectId("ModuleC")' | false                | null          | ''
        /*project == external*/ "2.0"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | null          | ''
        /*project >  external*/ "2.1"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | null          | ''
                                // === with preferProjectModules() ===
        /*project <  external*/ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | true                 | null          | ''
        /*project == external*/ "2.0"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | true                 | null          | ''
        /*project >  external*/ "2.1"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | true                 | null          | ''
                                // === without preferProjectModules() but dependency substitution ===
        /*project <  external*/ "1.9"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.0")' | 'projectId("ModuleC")' | false                | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:1.9')"
        /*project == external*/ "2.0"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.0")' | 'projectId("ModuleC")' | false                | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:2.0')"
        /*project >  external*/ "2.1"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.1")' | 'projectId("ModuleC")' | false                | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:2.1')"
                                // === with preferProjectModules() but dependency substitution <- no change, substitution wins ===
        /*project <  external*/ "1.9"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.0")' | 'projectId("ModuleC")' | true                 | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:1.9')"
        /*project == external*/ "2.0"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.0")' | 'projectId("ModuleC")' | true                 | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:2.0')"
        /*project >  external*/ "2.1"            | "2.0"           | 'moduleId("myorg", "ModuleC", "2.1")' | 'projectId("ModuleC")' | true                 | null          | "substitute project(':ModuleC') with module('myorg:ModuleC:2.1')"
                                // === with dependency substitution (same effect as preferProjectModules() only for the explicitly substituted dependency) ===
        /*project <  external*/ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | null          | "substitute module('myorg:ModuleC') with project(':ModuleC')"
        /*project == external*/ "2.0"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | null          | "substitute module('myorg:ModuleC') with project(':ModuleC')"
        /*project >  external*/ "2.1"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | null          | "substitute module('myorg:ModuleC') with project(':ModuleC')"
                                // === Force: without preferProjectModules() ===
        /*project != forced  */ "1.9"            | "2.0"           | 'moduleId("myorg", "ModuleC", "1.5")' | 'projectId("ModuleC")' | false                | '1.5'         | ''
        /*project == forced  */ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | '1.9'         | ''
        /*project != forced  */ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | '1.5'         | "substitute module('myorg:ModuleC') with project(':ModuleC')"
        /*project == forced  */ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | false                | '1.9'         | "substitute module('myorg:ModuleC') with project(':ModuleC')"
                                // === Force: with preferProjectModules() <- no change ===
        /*project != forced  */ "1.9"            | "2.0"           | 'moduleId("myorg", "ModuleC", "1.5")' | 'projectId("ModuleC")' | true                 | '1.5'         | ''
        /*project == forced  */ "1.9"            | "2.0"           | 'projectId("ModuleC")'                | 'projectId("ModuleC")' | true                 | '1.9'         | ''
    }


    static String check(String moduleName, String declaredDependencyId, String confName, String winner) { """
        task check${moduleName}_${confName} {
            doLast {
                def deps = configurations.${confName}.incoming.resolutionResult.allDependencies as List
                def projectDependency = deps.find {
                    it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly($declaredDependencyId)
                }

                assert projectDependency && projectDependency.selected.componentId == $winner
            }
        }
"""
    }

    static String checkHelper(String buildId, String projectPath) { """
        def moduleId(String group, String name, String version) {
            return org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(group, name, version)
        }

        def projectId(String projectName) {
            def buildId = $buildId
            def projectPath = $projectPath
            return new org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier(buildId, projectPath)
        }
"""
    }

}
