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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class JavaCleanAssemblePerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("clean assemble Java project - #testProject")
    def "clean assemble Java project"() {
        given:
        runner.testId = "clean assemble $testProject project" + (parallel ? " (parallel)" : "")
        runner.previousTestIds = parallel ? [] : ["full assemble Java build $testProject (daemon)"]
        runner.testProject = testProject
        runner.tasksToRun = ["clean", "assemble"]
        runner.gradleOpts = ["-Xms${maxMemory}", "-Xmx${maxMemory}"]
        runner.args = parallel ? ['-Dorg.gradle.parallel=true', '-Dorg.gradle.parallel.intra=true'] : []
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.targetVersions = ["3.5-20170223000042+0000"]
        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject            | maxMemory | parallel | warmUpRuns | runs
        "bigOldJavaMoreSource" | '608m'    | false    | 2          | 6
        "bigOldJava"           | '576m'    | false    | 2          | 6
        "mediumOldJava"        | '128m'    | false    | null       | null
        "smallOldJava"         | '128m'    | false    | null       | null
        "largeEnterpriseBuild" | '4g'      | false    | 2          | 6
        "largeEnterpriseBuild" | '4g'      | true     | 2          | 6
    }
}
