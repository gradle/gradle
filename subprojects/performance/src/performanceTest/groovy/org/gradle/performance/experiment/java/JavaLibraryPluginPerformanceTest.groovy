/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.experiment.java

import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT

@Category(SlowPerformanceRegressionTest)
class JavaLibraryPluginPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "java-library vs java on #testProject"() {
        def javaLibraryRuns = "java-library-plugin"
        def javaRuns = "java-plugin"
        def compileClasspathPackaging = OperatingSystem.current().windows

        given:
        runner.testGroup = "java plugins"
        runner.buildSpec {
            warmUpCount = warmUpRuns
            invocationCount = runs
            projectName(testProject.projectName).displayName(javaLibraryRuns).invocation {
                tasksToRun("clean", "classes").args("-PcompileConfiguration", "-Dorg.gradle.java.compile-classpath-packaging=$compileClasspathPackaging").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}")
            }
        }
        runner.baseline {
            warmUpCount = warmUpRuns
            invocationCount = runs
            projectName(testProject.projectName).displayName(javaRuns).invocation {
                tasksToRun("clean", "classes").args("-PcompileConfiguration", "-PnoJavaLibraryPlugin").gradleOpts("-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}")
            }
        }

        when:
        def results = runner.run()

        then:
        def javaResults = buildBaselineResults(results, javaRuns)
        def javaLibraryResults = results.buildResult(javaLibraryRuns)
        def speedStats = javaResults.getSpeedStatsAgainst(javaLibraryResults.name, javaLibraryResults)
        println(speedStats)
        if (javaResults.significantlyFasterThan(javaLibraryResults, 0.95)) {
            throw new AssertionError(speedStats)
        }

        where:
        testProject                   | warmUpRuns | runs
        LARGE_JAVA_MULTI_PROJECT      | 2          | 3
        // HUGE_JAVA_MULTI_PROJECT    | 2          | 3
    }

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }
}
