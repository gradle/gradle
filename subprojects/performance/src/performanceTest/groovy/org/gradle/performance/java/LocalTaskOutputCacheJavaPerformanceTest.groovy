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

package org.gradle.performance.java

import spock.lang.Unroll

@Unroll
class LocalTaskOutputCacheJavaPerformanceTest extends AbstractTaskOutputCacheJavaPerformanceTest {

    def "Builds '#testProject' calling #tasks with local cache"(String testProject, String heapSize, List<String> tasks) {
        given:
        runner.testId = "cached ${tasks.join(' ')} $testProject project"
        runner.previousTestIds = ["cached Java $testProject ${tasks.join(' ')} (daemon)"]
        runner.testProject = testProject
        runner.tasksToRun = tasks
        setupHeapSize(heapSize)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, heapSize, tasks] << scenarios
    }

    def "Builds '#testProject' calling #tasks with local cache - push only"(String testProject, String heapSize, List<String> tasks) {
        given:
        runner.testId = "cached ${tasks.join(' ')} $testProject project - local cache, push only"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        setupHeapSize(heapSize)
        /*
         * This is pretty slow, so we reduce the number of runs
         */
        runner.warmUpRuns = 8
        runner.runs = 20
        runner.setupCleanupOnOddRounds()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        [testProject, heapSize, tasks] << scenarios
    }
}
