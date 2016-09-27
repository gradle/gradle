/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.performance.categories.Experiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([Experiment])
class JavaSourceChangesFullAssembleDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("incremental full assemble Java build - #testProject")
    def "incremental full assemble Java build"() {
        given:
        runner.testId = "incremental full assemble Java build $testProject (daemon)"
        runner.previousTestIds = ["incremental build java project $testProject which doesn't declare any API"]
        runner.testProject = testProject
        runner.tasksToRun = ['assemble']
        runner.targetVersions = ['2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]
        runner.buildExperimentListener = new JavaSoftwareModelSourceFileUpdater(10, 0, 0)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << ["smallJavaSwModelCompileAvoidanceWithoutApi", "largeJavaSwModelCompileAvoidanceWithoutApi"]
    }
}
