/*
 * Copyright 2015 the original author or authors.
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
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([Experiment])
class JavaSoftwareModelBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Checking overhead of API stubbing when #cardinality.description")
    def "checks overhead of API stubbing when some files are updated"() {
        given:
        runner.testId = "overhead of API jar generation when ${cardinality.description}"
        runner.testProject = 'tinyJavaSwApiJarStubbingWithoutApi'
        runner.tasksToRun = ['project1:mainApiJar']
        runner.targetVersions = ['2.10', '2.11', 'last']
        runner.useDaemon = true
        runner.gradleOpts = ["-Xms2g", "-Xmx2g"]
        def updater = new JavaSoftwareModelSourceFileUpdater(100, 0, 0, cardinality)
        runner.buildExperimentListener = updater

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        cardinality << [SourceUpdateCardinality.ONE_FILE, SourceUpdateCardinality.ALL_FILES]
    }
}
