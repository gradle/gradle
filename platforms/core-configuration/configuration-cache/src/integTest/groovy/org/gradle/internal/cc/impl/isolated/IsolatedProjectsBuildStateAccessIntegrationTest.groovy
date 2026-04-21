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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsBuildStateAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem on settings-level access to mutable state of the parent build"() {
        settingsFile "build-logic/settings.gradle", """
            gradle.parent.$invocation
        """
        settingsFile """
            includeBuild "build-logic"
        """

        when:
        isolatedProjectsFails "help", "-Dorg.gradle.internal.isolated-projects.report-cross-build-access=true"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":build-logic")
            problemMessages.each {
                problem("Settings file 'build-logic/settings.gradle': line 2: $it")
            }
        }

        where:
        invocation                                                              | problemMessages
        "getTaskGraph()"                                                        | ["Build ':build-logic' cannot access Gradle.getTaskGraph on build ':'"]
        "getStartParameter()"                                                   | ["Build ':build-logic' cannot access Gradle.getStartParameter on build ':'"]
        "getLifecycle()"                                                        | ["Build ':build-logic' cannot access Gradle.getLifecycle on build ':'"]
        "getSharedServices()"                                                   | ["Build ':build-logic' cannot access Gradle.getSharedServices on build ':'"]
        "getProviders()"                                                        | ["Build ':build-logic' cannot access Gradle.getProviders on build ':'"]
        "getPlugins()"                                                          | ["Build ':build-logic' cannot access Gradle.getPlugins on build ':'"]
        "getPluginManager()"                                                    | ["Build ':build-logic' cannot access Gradle.getPluginManager on build ':'"]
        "getExtensions()"                                                       | ["Build ':build-logic' cannot access Gradle.getExtensions on build ':'"]
        "rootProject{}"                                                         | ["Build ':build-logic' cannot access Gradle.rootProject on build ':'"]
        "allprojects{}"                                                         | ["Build ':build-logic' cannot access Gradle.allprojects on build ':'"]
        "beforeProject{}"                                                       | ["Build ':build-logic' cannot access Gradle.beforeProject on build ':'"]
        "afterProject{}"                                                        | ["Build ':build-logic' cannot access Gradle.afterProject on build ':'"]
        "beforeSettings{}"                                                      | ["Build ':build-logic' cannot access Gradle.beforeSettings on build ':'"]
        "settingsEvaluated{}"                                                   | ["Build ':build-logic' cannot access Gradle.settingsEvaluated on build ':'"]
        "projectsLoaded{}"                                                      | ["Build ':build-logic' cannot access Gradle.projectsLoaded on build ':'"]
        "projectsEvaluated{}"                                                   | ["Build ':build-logic' cannot access Gradle.projectsEvaluated on build ':'"]
        "removeListener($projectEvaluationListenerDefinition)"                  | ["Build ':build-logic' cannot access Gradle.removeListener on build ':'"]
        // We report CC compatible listener
        "addListener($projectEvaluationListenerDefinition)"                     | ["Build ':build-logic' cannot access Gradle.addListener on build ':'"]
        // CC not-compatible listener already reported
        "addListener(new Object())"                                             | ["registration of listener on 'Gradle.addListener' is unsupported"]
        "apply([:])"                                                            | ["Build ':build-logic' cannot access Gradle.apply on build ':'"]
        "addProjectEvaluationListener($projectEvaluationListenerDefinition)"    | ["Build ':build-logic' cannot access Gradle.addProjectEvaluationListener on build ':'"]
        "removeProjectEvaluationListener($projectEvaluationListenerDefinition)" | ["Build ':build-logic' cannot access Gradle.removeProjectEvaluationListener on build ':'"]

        // Public API available on settings level only when rootProject is evaluated
        "rootProject {gradle.parent.getRootProject()}"                          | ["Build ':build-logic' cannot access Gradle.getRootProject on build ':'", "Build ':build-logic' cannot access Gradle.rootProject on build ':'"]
        "rootProject {gradle.parent.getIncludedBuilds()}"                       | ["Build ':build-logic' cannot access Gradle.getIncludedBuilds on build ':'", "Build ':build-logic' cannot access Gradle.rootProject on build ':'"]
        "rootProject {gradle.parent.includedBuild('build-logic')}"              | ["Build ':build-logic' cannot access Gradle.includedBuild on build ':'", "Build ':build-logic' cannot access Gradle.rootProject on build ':'"]
        "rootProject {gradle.parent.getDefaultProject()}"                       | ["Build ':build-logic' cannot access Gradle.getDefaultProject on build ':'", "Build ':build-logic' cannot access Gradle.rootProject on build ':'"]

        // Internal API
        "getProjectEvaluationBroadcaster()"                                     | ["Build ':build-logic' cannot access Gradle.getProjectEvaluationBroadcaster on build ':'"]
        "getSettings()"                                                         | ["Build ':build-logic' cannot access Gradle.getSettings on build ':'"]
        "getBuildListenerBroadcaster()"                                         | ["Build ':build-logic' cannot access Gradle.getBuildListenerBroadcaster on build ':'"]
        "getServices()"                                                         | ["Build ':build-logic' cannot access Gradle.getServices on build ':'"]
        "getProjectRegistry()"                                                  | ["Build ':build-logic' cannot access Gradle.getProjectRegistry on build ':'"]
    }

    private static String projectEvaluationListenerDefinition =
        """new ProjectEvaluationListener() {
            @Override void beforeEvaluate(Project project){}
            @Override void afterEvaluate(Project project, ProjectState state){}
        }
        """

    def "fails on settings-level access to unavailable state of the parent build"() {
        settingsFile "build-logic/settings.gradle", """
            def parentGradle = gradle.parent // wrapped
            gradle.$invocation
        """
        settingsFile """
            includeBuild "build-logic"
        """

        when:
        isolatedProjectsFails "help", "-Dorg.gradle.internal.isolated-projects.report-cross-build-access=true"

        then:
        failure.assertHasErrorOutput("This internal method should not be used.")

        where:
        invocation << [
            "parent.baseProjectClassLoaderScope()",
            "parent.includedBuilds()",
            "parent.attachSettings(null)",
            "parent.setClassLoaderScope(() -> gradle.classLoaderScope)",
            "parent.getClassLoaderScope()",
            "parent.setIncludedBuilds([])",
            "parent.setBaseProjectClassLoaderScope(gradle.classLoaderScope)",
            "parent.resetState()",
            "parent.getOwner()",

            // Internal API, available on settings level only when rootProject is evaluated.
            // In the end, we need to get a ProjectInternal instance somehow to try to set it to the parent

            // We use "pre-wrapped" parentGradle because calling `gradle.parent` within the closure will return cross-project reporting Gradle,
            // which doesn't prohibit these calls.
            "rootProject {parentGradle.setRootProject(it)}",
            "rootProject {parentGradle.setDefaultProject(it)} ",
        ]
    }

    def "does not report a cross-build access problem by default"() {
        settingsFile"build-logic/settings.gradle", """
            gradle.parent.getSharedServices()
        """
        settingsFile """
            includeBuild "build-logic"
        """

        when:
        isolatedProjectsRun "help"

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":build-logic")
        }
    }
}
