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

import static org.gradle.performance.generator.JavaTestProject.*

@Ignore('MEDIUM_JAVA_COMPOSITE_BUILD contains buildSrc')
class JavaCleanAssemblePerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "clean assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = ["4.9-20180620235919+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                            | warmUpRuns | runs
        LARGE_MONOLITHIC_JAVA_PROJECT          | 2          | 6
        LARGE_JAVA_MULTI_PROJECT               | 2          | 6
        MEDIUM_JAVA_COMPOSITE_BUILD            | 2          | 6
        MEDIUM_JAVA_PREDEFINED_COMPOSITE_BUILD | 2          | 6
    }
}
