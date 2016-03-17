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

import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import spock.lang.Unroll
import static org.gradle.performance.measure.Duration.millis

class ToolingApiEclipseModelCrossVersionPerformanceTest extends AbstractToolingApiCrossVersionPerformanceTest {

    @Unroll
    def "building eclipse model for a #project Java project"() {
        given:
        rootDir {
            'build.gradle'('// this is a sample build')
            'settings.gradle'(this.settings(size))
            size.times { n ->
                "project${n + 1}" {
                    'build.gradle'('''
                    apply plugin: "java"
                    ''')
                }
            }
        }

        experiment("$project Java project", "get $project HierarchicalEclipseProject model") {
            warmUpCount = 3
            invocationCount = 10
            maxExecutionTimeRegression = millis(maxRegressionTime)
            action {
                getModel(HierarchicalEclipseProject)
            }
        }

        when:
        def results = performMeasurements()

        then:
        noExceptionThrown()

        where:
        project  | size | maxRegressionTime
        "small"  | 5    | 20
        "medium" | 30   | 100
        "large"  | 100  | 100
    }

    private String settings(int size) {
        (0..size).collect {
            "include 'project${it + 1}'"
        }.join('\n')
    }
}
