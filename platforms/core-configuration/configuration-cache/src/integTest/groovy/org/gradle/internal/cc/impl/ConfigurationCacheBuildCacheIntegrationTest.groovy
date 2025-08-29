/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl


import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

class ConfigurationCacheBuildCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements DirectoryBuildCacheFixture {

    def configurationCache = new ConfigurationCacheFixture(this)

    @Issue("https://github.com/gradle/gradle/issues/32542")
    def "cacheable task fails and neither task nor configuration are cached due to execution-time problem"() {
        // Ensure some sources for compilation
        javaFile "src/main/java/Main.java", """public class Main {}"""

        buildFile """
            plugins { id("java") }

            version = "foo-version"
            tasks.compileJava { // using a cacheable task
                doLast {
                    println("At execution: version='\${project.version}'")
                }
            }
        """

        when:
        withBuildCache()
        configurationCacheFails "compileJava"

        then:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureCauseContains("Invocation of 'Task.project' by task ':compileJava' at execution time is unsupported with the configuration cache.")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }

        when: "running again"
        withBuildCache()
        configurationCacheFails "compileJava"

        then: "the task is still executed and fails"
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureCauseContains("Invocation of 'Task.project' by task ':compileJava' at execution time is unsupported with the configuration cache.")

        and: "configuration cache is not reused"
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedOutsideBuildFailure = true
            problem "Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "in warning mode, cacheable task is cached and configuration reused despite execution time problem"() {
        // Ensure some sources for compilation
        javaFile "src/main/java/Main.java", """public class Main {}"""

        buildFile """
            plugins { id("java") }

            version = "foo-version"
            tasks.compileJava { // using a cacheable task
                doLast {
                    println("At execution: version='\${project.version}'")
                }
            }
        """

        when:
        withBuildCache()
        configurationCacheRunLenient "compileJava"

        then:
        executedAndNotSkipped(":compileJava")
        outputContains("At execution: version='unspecified'")

        and:
        configurationCache.assertStateStoredWithProblems {
            problem("Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }

        when: "running again"
        withBuildCache()
        configurationCacheRunLenient "compileJava"

        then:
        skipped(":compileJava")
        outputContains("> Task :compileJava UP-TO-DATE")

        configurationCache.assertStateLoaded() // the execution-time problem is not observed, because the task is skipped

        when:
        file("build").deleteDir() // ensure task is not up-to-date without getting a CC miss

        withBuildCache()
        configurationCacheRunLenient "compileJava"

        then:
        skipped(":compileJava")
        outputContains("> Task :compileJava FROM-CACHE")
        configurationCache.assertStateLoaded() // the execution-time problem is not observed, because the task is skipped
    }
}
