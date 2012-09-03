/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.peformance

import org.gradle.peformance.fixture.PerformanceTestRunner
import spock.lang.Specification
import spock.lang.Unroll

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {
    @Unroll("Project '#testProject' clean build")
    def "clean build"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['clean', 'build'],
                runs: runs,
                warmUpRuns: 1,
                accuracyMs: accuracyMs
        ).run()
        result.assertCurrentReleaseIsNotSlower()
        result.assertMemoryUsed(0.01)

        where:
        testProject       | runs | accuracyMs
        "small"           | 5    | 500
        "multi"           | 5    | 1000
        "lotDependencies" | 5    | 1000
    }

    @Unroll("Project '#testProject' up-to-date build")
    def "build"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['build'],
                runs: runs,
                warmUpRuns: 1,
                accuracyMs: accuracyMs
        ).run()
        result.assertCurrentReleaseIsNotSlower()
        result.assertMemoryUsed(0.01)

        where:
        testProject       | runs | accuracyMs
        "small"           | 5    | 500
        "multi"           | 5    | 1000
        "lotDependencies" | 5    | 1000
    }

    @Unroll("Project '#testProject' dependency report")
    def "dependency report"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['dependencyReport'],
                runs: runs,
                warmUpRuns: 1,
                accuracyMs: accuracyMs
        ).run()
        result.assertCurrentReleaseIsNotSlower()
        result.assertMemoryUsed(0.1)

        where:
        testProject       | runs | accuracyMs
        "lotDependencies" | 5    | 3000
    }

    @Unroll("Project '#testProject' eclipse")
    def "eclipse"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['eclipse'],
                runs: runs,
                warmUpRuns: 1,
                accuracyMs: accuracyMs
        ).run()
        result.assertCurrentReleaseIsNotSlower()
        result.assertMemoryUsed(0.01)

        where:
        testProject       | runs | accuracyMs
        "small"           | 5    | 500
        "multi"           | 5    | 1000
//        "lotDependencies" | 5    | 1000
    }

    @Unroll("Project '#testProject' idea")
    def "idea"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject,
                tasksToRun: ['idea'],
                runs: runs,
                warmUpRuns: 1,
                accuracyMs: accuracyMs
        ).run()
        result.assertCurrentReleaseIsNotSlower()
        result.assertMemoryUsed(0.01)

        where:
        testProject       | runs | accuracyMs
        "small"           | 5    | 500
        "multi"           | 5    | 1000
//        "lotDependencies" | 5    | 1000
    }
}