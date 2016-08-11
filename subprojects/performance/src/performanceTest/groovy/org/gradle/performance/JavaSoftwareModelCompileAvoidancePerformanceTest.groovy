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
class JavaSoftwareModelCompileAvoidancePerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll("Compile avoidance for #size project")
    def "build java software model project"() {
        given:
        runner.testGroup = "compile avoidance using Java software model"
        runner.testId = "$size project compile avoidance"

        // note to the reader: we cannot rely on @Unroll because the report aggregation works by test execution, so all need
        // to be executed as part of a single test method
        def scenarios = [
            // nonApiChanges, abiCompatibleChanges and abiBreakingChanges are expressed in percentage of projects that are
            // going to be updated in the test
            'internal API changes': [10, 0, 0],
            'ABI compatible changes': [0, 10, 0],
            'ABI breaking changes': [0, 0, 10]
        ]

        def apiTypes = [
            WithApi: 'with declared API',
            WithoutApi: 'without declared API'
        ]

        scenarios.each { name, changes ->
            def (nonApiChanges, abiCompatibleChanges, abiBreakingChanges) = changes
            apiTypes.each { apiType, apiDesc ->
                String projectDir = "${size}JavaSwModelCompileAvoidance${apiType}"
                runner.baseline {
                    projectName(projectDir).displayName("$name $apiDesc").invocation {
                        tasksToRun('assemble').useDaemon().gradleOpts('-Xms2G', '-Xmx2G')
                    }.listener(new JavaSoftwareModelSourceFileUpdater(nonApiChanges, abiCompatibleChanges, abiBreakingChanges))
                }
            }
        }


        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        size << ['small', 'large']

    }
}
