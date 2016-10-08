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

@Category(JavaPerformanceTest)
class JavaUpToDateFullBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Up-to-date full build - #testProject")
    def "up-to-date full build Java build"() {
        given:
        runner.testId = "up-to-date full build Java build $testProject"
        runner.previousTestIds = ["up-to-date build $testProject"]
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.targetVersions = targetVersions

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | targetVersions
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was introduced by some code which makes parallel
        // execution and non-up-to-date cases work much better. That is
        // currently a more important use case, so we are accepting the
        // performance regression in these fully up-to-date cases.
        "small"           | ['3.2-20161004202618+0000']
        "multi"           | ['3.2-20161004202618+0000']
        "lotDependencies" | ['3.2-20161004202618+0000']
    }

    @Unroll("Up-to-date full build (daemon) - #testProject")
    def "up-to-date full build Java build with daemon"() {
        given:
        runner.testId = "up-to-date full build Java build $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['build']
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was determined acceptable in this discussion:
        // https://issues.gradle.org/browse/GRADLE-1346
        runner.targetVersions = ['3.2-20160915000027+0000']
        runner.useDaemon = true
        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
        where:
        testProject << ["bigOldJava", "lotDependencies"]
    }
}
