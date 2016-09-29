/*
 * Copyright 2012 the original author or authors.
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
class JavaFullBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("full build Java build - #testProject")
    def "full build Java build"() {
        given:
        runner.testId = "full build Java build $testProject"
        runner.previousTestIds = ["clean build $testProject"]
        runner.testProject = testProject
        runner.tasksToRun = ['clean', 'build']
        runner.targetVersions = targetVersions

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | targetVersions
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was introduced by some code which makes parallel
        // execution and cases work much better. That is currently a more
        // important use case, so we are accepting the very modest (~2%)
        // performance regression in these non-parallel cases.
        "small"           | ['3.2-20160922000020+0000']
        "multi"           | ['3.2-20160922000020+0000']
        "lotDependencies" | ['3.2-20160922000020+0000']
    }
}
