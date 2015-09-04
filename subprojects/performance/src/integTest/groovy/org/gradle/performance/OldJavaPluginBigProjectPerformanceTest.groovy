/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance

import spock.lang.Unroll

import static org.gradle.performance.measure.DataAmount.mbytes
import static org.gradle.performance.measure.Duration.millis

class OldJavaPluginBigProjectPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("#scenario build")
    def "build"() {
        given:
        runner.testId = "big project old java plugin $scenario build"
        runner.testProject = "bigOldJavaMoreSource"
        runner.useDaemon = true
        runner.tasksToRun = tasks
        runner.maxExecutionTimeRegression = millis(500)
        runner.maxMemoryRegression = mbytes(200)
        runner.targetVersions = ['2.0', '2.2.1', '2.4', 'last']
        runner.gradleOpts = ["-Xmx1024m", "-XX:MaxPermSize=256m"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        scenario  | tasks
        "empty"   | ["help"]
        "full"    | ["clean", "assemble"]
        "partial" | [":project1:clean", ":project1:assemble"]
    }
}
