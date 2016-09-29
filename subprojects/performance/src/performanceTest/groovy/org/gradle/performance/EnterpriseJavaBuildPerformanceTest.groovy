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
import org.gradle.performance.categories.JavaPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest, Experiment])
class EnterpriseJavaBuildPerformanceTest extends AbstractAndroidPerformanceTest {

    @Unroll("Builds '#testProject' calling #tasks (daemon)")
    def "build"() {
        // This is just an approximation of first use. We simply recompile the scripts
        given:
        runner.testId = "Enterprise Java $testProject ${tasks.join(' ')} (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.useDaemon = true
        runner.targetVersions = ['last']
        runner.buildExperimentListener = new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo info) {
                def serverDir = new File(info.projectDir, "maven-server")
                new File(serverDir, "gradlew").executable = true
            }
        }
        runner.gradleOpts = ["-Xms8g", "-Xmx8g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject          | tasks
        'largeEnterpriseBuild' | ['cleanIdea', 'idea']
        'largeEnterpriseBuild' | ['clean', 'assemble']
    }
}
