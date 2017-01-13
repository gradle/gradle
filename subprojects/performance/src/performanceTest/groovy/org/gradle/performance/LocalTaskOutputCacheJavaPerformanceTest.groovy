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

import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest])
class LocalTaskOutputCacheJavaPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Builds '#testProject' calling #tasks (daemon) with local cache")
    def "build with cache"() {
        given:
        runner.testId = "cached Java $testProject ${tasks.join(' ')} (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms768m", "-Xmx768m"]
        runner.args = ['-Dorg.gradle.cache.tasks=true']
        /*
         * Since every second build is a 'clean', we need more iterations
         * than usual to get reliable results.
         */
        runner.runs = 40
        runner.setupCleanupOnOddRounds()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject      | tasks
        'bigOldJava'     | ['assemble']
        'largeWithJUnit' | ['build']
    }
}
