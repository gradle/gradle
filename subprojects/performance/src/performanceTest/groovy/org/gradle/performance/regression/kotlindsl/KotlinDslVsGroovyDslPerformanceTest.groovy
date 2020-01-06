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
package org.gradle.performance.regression.kotlindsl

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL

@Category(PerformanceRegressionTest)
class KotlinDslVsGroovyDslPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "help on #kotlinProject vs. help on #groovyProject"() {

        given:
        runner.testGroup = 'Kotlin DSL vs Groovy DSL'
        def groovyDslBuildName = 'Groovy DSL build'
        def kotlinDslBuildName = 'Kotlin DSL build'

        and:
        def warmupBuilds = 1
        def measuredBuilds = 1

        and:
        runner.baseline {
            displayName groovyDslBuildName
            projectName groovyProject.projectName
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                gradleOptions = ["-Xms${groovyProject.daemonMemory}", "-Xmx${groovyProject.daemonMemory}"]
                tasksToRun("help")
            }
        }

        and:
        runner.buildSpec {
            displayName kotlinDslBuildName
            projectName kotlinProject.projectName
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            invocation {
                gradleOptions = ["-Xms${kotlinProject.daemonMemory}", "-Xmx${kotlinProject.daemonMemory}"]
                tasksToRun("help")
            }
        }

        when:
        def results = runner.run()

        then:
        def groovyDslResults = buildBaselineResults(results, groovyDslBuildName)
        def kotlinDslResults = results.buildResult(kotlinDslBuildName)

        then:
        def speedStats = groovyDslResults.getSpeedStatsAgainst(kotlinDslResults.name, kotlinDslResults)
        println(speedStats)

        and:
        def shiftedGroovyResults = buildShiftedResults(results, groovyDslBuildName)
        if (shiftedGroovyResults.significantlyFasterThan(kotlinDslResults)) {
            throw new AssertionError(speedStats)
        }

        where:
        kotlinProject                       | groovyProject
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | LARGE_JAVA_MULTI_PROJECT
    }

    private static BaselineVersion buildBaselineResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        baselineResults.results.addAll(results.buildResult(name))
        return baselineResults
    }

    // TODO rebaseline overtime, remove when reaching 0

    private static int medianPercentageShift = 15

    private static BaselineVersion buildShiftedResults(CrossBuildPerformanceResults results, String name) {
        def baselineResults = new BaselineVersion(name)
        baselineResults.results.name = name
        def rawResults = results.buildResult(name)
        def shift = rawResults.totalTime.median.value * medianPercentageShift / 100
        baselineResults.results.addAll(rawResults.collect {
            new MeasuredOperation([totalTime: Amount.valueOf(it.totalTime.value + shift, it.totalTime.units), exception: it.exception])
        })
        return baselineResults
    }
}
