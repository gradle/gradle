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
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class JavaFirstUsePerformanceTest extends AbstractCrossVersionPerformanceTest {

    /* This test has some kind of consistent bias
     * against the current development version, which makes it fail
     * even when compared to yesterday's nightly. We need to find the
     * source of this problem before we can reactivate this test.
     *
     * Since '--recompile-scripts' is going to be deprecated, this
     * test should be changes to use a test listener that completely deletes
     * all caches between runs.
     */
    @Ignore
    @Unroll
    def "first use of #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.args = ['--recompile-scripts'] // This is an approximation of first use: we recompile the scripts
        runner.useDaemon = false
        runner.targetVersions = ["4.5-20171117235935+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | _
        LARGE_MONOLITHIC_JAVA_PROJECT | _
        LARGE_JAVA_MULTI_PROJECT      | _
    }

    @Unroll
    def "cold daemon on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.targetVersions = ["4.5-20171117235935+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | _
        LARGE_MONOLITHIC_JAVA_PROJECT | _
        LARGE_JAVA_MULTI_PROJECT      | _
    }
}
