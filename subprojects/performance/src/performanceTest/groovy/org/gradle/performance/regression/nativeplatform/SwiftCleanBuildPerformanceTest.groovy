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

package org.gradle.performance.regression.nativeplatform

import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(SlowPerformanceRegressionTest)
class SwiftCleanBuildPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    def setup() {
        runner.minimumBaseVersion = '4.6'
        runner.targetVersions = ["6.2-20200108160029+0000"]
        runner.args += ["--parallel", "--${ParallelismBuildOptions.MaxWorkersOption.LONG_OPTION}=6"]
    }

    @Unroll
    def "clean assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | maxMemory
        'mediumSwiftMulti' | '1G'
        'bigSwiftApp'      | '1G'
    }

}
