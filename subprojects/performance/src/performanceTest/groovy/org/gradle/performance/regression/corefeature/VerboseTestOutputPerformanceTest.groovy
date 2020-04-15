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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import spock.lang.Unroll

class VerboseTestOutputPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    @Unroll
    def "cleanTest test on #testProject with verbose test output"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['cleanTest', 'test']
        runner.args = ['-q']
        runner.gradleOpts = ["-Xms256m", "-Xmx256m"]
        runner.targetVersions = ["6.2-20200108160029+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject         | _
        "withVerboseTestNG" | _
        "withVerboseJUnit"  | _
    }
}
