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
class JavaUpToDateFullAssembleDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Up-to-date assemble Java software model build - #testProject")
    def "up-to-date assemble Java software model build"() {
        given:
        runner.testId = "up-to-date full assemble Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['assemble']
        runner.targetVersions = ['2.10', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << ["smallJavaSwModelProject", "largeJavaSwModelProject", "smallJavaSwModelCompileAvoidanceWithoutApi",
                        "largeJavaSwModelCompileAvoidanceWithoutApi", "bigNewJava", "mediumNewJava", "smallNewJava"]
    }

    @Unroll("Up-to-date parallel full assemble Java build - #testProject")
    def "up-to-date parallel full assemble Java build"() {
        given:
        runner.testId = "up-to-date full assemble Java build $testProject (daemon, parallel)"
        runner.testProject = testProject
        runner.tasksToRun = ['assemble']
        runner.targetVersions = ['2.10', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]
        runner.args += ["--parallel", "--max-workers=4"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject << ["smallJavaSwModelProject", "largeJavaSwModelProject"]
    }
}
