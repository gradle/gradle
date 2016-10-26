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

import org.gradle.performance.categories.AndroidPerformanceTest
import org.gradle.performance.categories.Experiment
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([AndroidPerformanceTest, Experiment])
class RealLifeAndroidBuildPerformanceTest extends AbstractAndroidPerformanceTest {

    @Unroll("Builds '#testProject' calling #tasks (daemon)")
    def "build"() {
        // This is just an approximation of first use. We simply recompile the scripts
        given:
        runner.testId = "Android $testProject ${tasks.join(' ')} (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.useDaemon = true
        runner.targetVersions = ['last']
        runner.gradleOpts = testProject.startsWith('medium') ? ["-Xms2g", "-Xmx2g"] : ["-Xms8g", "-Xmx12g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject          | tasks
        //'mediumAndroidBuild' | ['help']
        'mediumAndroidBuild' | ['clean', 'assemble']
        //'largeAndroidBuild'  | ['help']
        //'largeAndroidBuild'  | ['clean', 'assemble']
    }
}
