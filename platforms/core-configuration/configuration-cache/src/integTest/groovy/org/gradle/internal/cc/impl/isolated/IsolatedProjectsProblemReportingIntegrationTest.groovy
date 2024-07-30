/*
 * Copyright 2024 the original author or authors.
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

class IsolatedProjectsProblemReportingIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "stops reporting problems at certain limits collecting all stacktraces"() {
        settingsFile """
            include(":a")
        """
        createDir("a")

        for (i in 1..530) {
            buildFile << 'project(":a").version\n'
        }

        when:
        isolatedProjectsFails("help")

        then:
        outputContains("Configuration cache entry discarded with 530 problems.")

        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 1: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 10: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 100: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 101: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 102: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 103: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 104: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 105: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 106: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 107: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 108: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 109: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 11: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 110: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            withProblem("Build file 'build.gradle': line 111: Project ':' cannot access 'Project.version' functionality on another project ':a'")
            totalProblemsCount = 530
            problemsWithStackTraceCount = 530
        }

        failure.assertHasFailure("Configuration cache problems found in this build.") { failure ->
            failure.assertHasCauses(5)
        }
    }
}
