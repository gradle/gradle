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

import org.gradle.performance.categories.GradleCorePerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(GradleCorePerformanceTest)
class DependencyResolutionPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Resolves dependencies from #repoType repository - #testProject (daemon)")
    def "full build Java build"() {
        given:
        runner.testId = "resolves dependencies from $repoType repository $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['resolveDependencies']
        runner.targetVersions = targetVersions
        runner.useDaemon = true

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject              | repoType | targetVersions
        // TODO(pepper): Revert this to 'last' when 3.2 is released
        // The regression was determined acceptable in this discussion:
        // https://issues.gradle.org/browse/GRADLE-1346
        "lotDependencies"        | 'local'  | ['3.3-20161028000018+0000']
        "lotProjectDependencies" | 'local'  | ['3.2-rc-1']
    }
}
