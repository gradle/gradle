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

class IsolatedProjectsStartParameterAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "reports a problem when a build script mutates another project's StartParameter via #invocation"() {
        createDirs("b")
        settingsFile """
            include("b")
        """
        buildFile """
            import org.gradle.api.logging.LogLevel
            project(':b').gradle.startParameter.$invocation
        """

        when:
        isolatedProjectsFails "help"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":b")
            // Cross-project access to `gradle` itself is reported, plus the StartParameter mutation.
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.gradle' functionality on another project ':b'")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'StartParameter.$method' functionality")
        }

        where:
        invocation                                       | method
        "setMaxWorkerCount(2)"                           | "setMaxWorkerCount"
        "setParallelProjectExecutionEnabled(true)"       | "setParallelProjectExecutionEnabled"
        "setOffline(true)"                               | "setOffline"
        "setBuildCacheEnabled(true)"                     | "setBuildCacheEnabled"
        "setDryRun(true)"                                | "setDryRun"
        "setRefreshDependencies(true)"                   | "setRefreshDependencies"
        "setRerunTasks(true)"                            | "setRerunTasks"
        "setContinueOnFailure(true)"                     | "setContinueOnFailure"
        "setLogLevel(LogLevel.INFO)"                     | "setLogLevel"
        "setTaskNames(['help'])"                         | "setTaskNames"
        "setExcludedTaskNames(['x'])"                    | "setExcludedTaskNames"
        "setProjectProperties([:])"                      | "setProjectProperties"
        "setSystemPropertiesArgs([:])"                   | "setSystemPropertiesArgs"
        "setInitScripts([])"                             | "setInitScripts"
        "addInitScript(file('init.gradle'))"             | "addInitScript"
        "includeBuild(file('inc'))"                      | "includeBuild"
        "setBuildProjectDependencies(false)"             | "setBuildProjectDependencies"
        "setConfigureOnDemand(true)"                     | "setConfigureOnDemand"
    }

    def "cross-project StartParameter mutable-collection getter returns an immutable view (#invocation)"() {
        createDirs("b")
        settingsFile """
            include("b")
        """
        buildFile """
            project(':b').gradle.startParameter.$invocation
        """

        when:
        isolatedProjectsFails "help"

        then:
        // The wrapper returns Guava ImmutableX.copyOf(...) — mutation throws UnsupportedOperationException.
        failure.assertHasErrorOutput("UnsupportedOperationException")

        where:
        invocation << [
            "projectProperties.put('foo', 'bar')",
            "systemPropertiesArgs.put('foo', 'bar')",
            "excludedTaskNames.add('foo')",
            "taskRequests.clear()",
            "lockedDependenciesToUpdate.add('foo')",
            "writeDependencyVerifications.add('sha256')",
        ]
    }

    def "own-project StartParameter mutation is not reported"() {
        settingsFile """
            rootProject.name = 'root'
        """
        buildFile """
            // Own-project access goes through DefaultProject.getGradle() which uses the
            // non-cross-project wrapper, so the StartParameter wrapper is NOT engaged.
            gradle.startParameter.setMaxWorkerCount(2)
            gradle.startParameter.setOffline(true)
        """

        when:
        isolatedProjectsRun "help"

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }

    def "read-only cross-project StartParameter access still reports cross-project gradle access but not StartParameter access"() {
        createDirs("b")
        settingsFile """
            include("b")
        """
        buildFile """
            println project(':b').gradle.startParameter.maxWorkerCount
            println project(':b').gradle.startParameter.taskNames
            println project(':b').gradle.startParameter.projectProperties.keySet()
        """

        when:
        isolatedProjectsFails "help"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":b")
            // Each cross-project `project(':b').gradle` access is reported.
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.gradle' functionality on another project ':b'")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.gradle' functionality on another project ':b'")
            problem("Build file 'build.gradle': line 4: Project ':' cannot access 'Project.gradle' functionality on another project ':b'")
            // No StartParameter.<method> problem is expected for the read-only accesses themselves.
        }
    }
}
