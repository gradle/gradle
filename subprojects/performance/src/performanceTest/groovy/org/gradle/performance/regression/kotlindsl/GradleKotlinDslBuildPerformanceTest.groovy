/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.junit.Ignore
import spock.lang.Unroll

@Ignore
class GradleKotlinDslBuildPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    @Unroll
    def "configuration of #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.gradleOpts = ["-Xms512m", "-Xmx512m"]
        runner.targetVersions = ["6.2-20200108160029+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | _
        "ktsSmall"        | _
        "ktsManyProjects" | _
    }

    @Unroll
    def "first use of #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.gradleOpts = ["-Xms512m", "-Xmx512m"]
        runner.targetVersions = ["6.2-20200108160029+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | warmUpRuns | runs
        "ktsSmall"        | null       | null
        "ktsManyProjects" | 5          | 10
    }
}
