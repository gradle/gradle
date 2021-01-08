/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.fixture


import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
@CompileStatic
class PerformanceTestScenarioDefinitionVerifier {
    static void main(String[] args) {
        File oldFile = new File(args[0])
        File newFile = new File(args[1])
        PerformanceTestScenarioDefinition oldJson = new ObjectMapper().readValue(oldFile, PerformanceTestScenarioDefinition).sort()
        PerformanceTestScenarioDefinition newJson = new ObjectMapper().readValue(newFile, PerformanceTestScenarioDefinition).sort()
        if (oldJson != newJson) {
            int size = Math.min(oldJson.performanceTests.size(), newJson.performanceTests.size())
            int firstDifferentElementIndex = size
            for (int i = 0; i < size; ++i) {
                if (oldJson.performanceTests[i] != newJson.performanceTests[i]) {
                    firstDifferentElementIndex = i
                    break
                }
            }
            throw new IllegalStateException("""
Scenario JSON file verification fails at index ${firstDifferentElementIndex}. To update the scenario file run the 'performance:writePerformanceScenarioDefinitions' task.
Please see ${oldFile.getAbsolutePath()} and ${newFile.getAbsolutePath()} for details
Old: ${firstDifferentElementIndex < oldJson.performanceTests.size() ? oldJson.performanceTests[firstDifferentElementIndex] : ""}
New: ${firstDifferentElementIndex < newJson.performanceTests.size() ? newJson.performanceTests[firstDifferentElementIndex] : ""}
""")
        }
    }
}
