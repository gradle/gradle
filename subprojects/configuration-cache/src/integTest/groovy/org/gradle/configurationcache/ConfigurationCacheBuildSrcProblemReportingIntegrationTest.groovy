/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

class ConfigurationCacheBuildSrcProblemReportingIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "does not report configuration and runtime problems in buildSrc"() {
        file("buildSrc/build.gradle") << """
            // These should not be reported, as neither of these are serialized
            gradle.buildFinished { }
            classes {
                inputs.property('p', project)
                doLast { t -> t.project }
            }
        """
        buildFile << """
            tasks.register("broken") {
                gradle.addListener(new BuildAdapter())
                inputs.property('p', project).optional(true)
                doLast { }
            }
            tasks.register("ok") {
                doLast { }
            }
        """

        when:
        configurationCacheRun("ok")

        then:
        result.assertHasPostBuildOutput("Configuration cache entry stored.")

        when:
        configurationCacheRun("ok")

        then:
        result.assertHasPostBuildOutput("Configuration cache entry reused.")

        when:
        configurationCacheFails("broken")

        then:
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.addListener' is unsupported")
            withProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient("broken")

        then:
        postBuildOutputContains("Configuration cache entry stored with 3 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.addListener' is unsupported")
            withProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }

        when:
        configurationCacheRunLenient("broken")

        then:
        postBuildOutputContains("Configuration cache entry reused with 1 problem.")
        problems.assertResultHasProblems(result) {
            withProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
    }

    def "reports problems in buildSrc tasks that run in main build"() {
        file("buildSrc/build.gradle") << """
            // These should not be reported, as these are run during configuration
            gradle.buildFinished { }
            classes {
                inputs.property('p', project)
                doLast { t -> t.project }
            }
            // These should be reported
            task broken {
                inputs.property('p', project).optional(true)
                doLast { t -> t.project }
            }
        """

        when:
        configurationCacheFails("buildSrc:broken")

        then:
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'buildSrc/build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.".replace('/', File.separator))
            withProblem("Task `:buildSrc:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient("buildSrc:broken")

        then:
        postBuildOutputContains("Configuration cache entry stored with 3 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'buildSrc/build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.".replace('/', File.separator))
            withProblem("Task `:buildSrc:broken` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:buildSrc:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }

        when:
        configurationCacheRunLenient("buildSrc:broken")

        then:
        postBuildOutputContains("Configuration cache entry reused with 2 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'buildSrc/build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.".replace('/', File.separator))
            withProblem("Task `:buildSrc:broken` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 1
        }
    }
}
