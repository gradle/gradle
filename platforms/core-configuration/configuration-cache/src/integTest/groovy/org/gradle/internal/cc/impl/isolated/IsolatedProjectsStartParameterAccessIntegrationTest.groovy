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

    def "reports a problem when build script mutates StartParameter via #invocation"() {
        settingsFile """
            rootProject.name = 'root'
        """
        buildFile """
            import org.gradle.api.logging.LogLevel
            $invocation
        """

        when:
        isolatedProjectsFails "help"

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'StartParameter.$method' functionality")
        }

        where:
        invocation                                                 | method
        "gradle.startParameter.setMaxWorkerCount(2)"               | "setMaxWorkerCount"
        "gradle.startParameter.setParallelProjectExecutionEnabled(true)" | "setParallelProjectExecutionEnabled"
        "gradle.startParameter.setOffline(true)"                   | "setOffline"
        "gradle.startParameter.setBuildCacheEnabled(true)"         | "setBuildCacheEnabled"
        "gradle.startParameter.setDryRun(true)"                    | "setDryRun"
        "gradle.startParameter.setRefreshDependencies(true)"       | "setRefreshDependencies"
        "gradle.startParameter.setRerunTasks(true)"                | "setRerunTasks"
        "gradle.startParameter.setContinueOnFailure(true)"         | "setContinueOnFailure"
        "gradle.startParameter.setLogLevel(LogLevel.INFO)"         | "setLogLevel"
        "gradle.startParameter.setTaskNames(['help'])"             | "setTaskNames"
        "gradle.startParameter.setExcludedTaskNames(['x'])"        | "setExcludedTaskNames"
        "gradle.startParameter.setProjectProperties([:])"          | "setProjectProperties"
        "gradle.startParameter.setSystemPropertiesArgs([:])"       | "setSystemPropertiesArgs"
        "gradle.startParameter.setInitScripts([])"                 | "setInitScripts"
        "gradle.startParameter.addInitScript(file('init.gradle'))" | "addInitScript"
        "gradle.startParameter.includeBuild(file('inc'))"          | "includeBuild"
        "gradle.startParameter.setBuildProjectDependencies(false)" | "setBuildProjectDependencies"
        "gradle.startParameter.setConfigureOnDemand(true)"         | "setConfigureOnDemand"
    }

    def "returns immutable view from mutable-collection getter #invocation"() {
        settingsFile """
            rootProject.name = 'root'
        """
        buildFile """
            $invocation
        """

        when:
        isolatedProjectsFails "help"

        then:
        // The wrapper returns Guava ImmutableX.copyOf(...) — mutation throws UnsupportedOperationException.
        failure.assertHasErrorOutput("UnsupportedOperationException")

        where:
        invocation << [
            "gradle.startParameter.projectProperties.put('foo', 'bar')",
            "gradle.startParameter.systemPropertiesArgs.put('foo', 'bar')",
            "gradle.startParameter.excludedTaskNames.add('foo')",
            "gradle.startParameter.taskRequests.clear()",
            "gradle.startParameter.lockedDependenciesToUpdate.add('foo')",
            "gradle.startParameter.writeDependencyVerifications.add('sha256')",
        ]
    }

    def "does not report a problem on read-only StartParameter access"() {
        settingsFile """
            rootProject.name = 'root'
        """
        buildFile """
            println gradle.startParameter.maxWorkerCount
            println gradle.startParameter.offline
            println gradle.startParameter.taskNames
            println gradle.startParameter.projectProperties.keySet()
        """

        when:
        isolatedProjectsRun "help"

        then:
        fixture.assertStateStored {
            projectsConfigured(":")
        }
    }
}
