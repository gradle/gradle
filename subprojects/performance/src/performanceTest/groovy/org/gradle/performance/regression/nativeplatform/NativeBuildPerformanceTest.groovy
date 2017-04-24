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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class NativeBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll
    def "clean assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["clean", "assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.runs = iterations
        runner.warmUpRuns = iterations
        runner.targetVersions = ["4.0-20170412191037+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject    | maxMemory    | iterations
        "smallNative"  | '256m'       | 40
        "mediumNative" | '256m'       | null
        "bigNative"    | '1g'         | null
        "multiNative"  | '256m'       | null
    }

    def "clean assemble on manyProjectsNative"() {
        given:
        runner.testProject = "manyProjectsNative"
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = ["4.0-20170412191037+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
