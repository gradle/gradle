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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * See .teamcity/performance-tests-ci.json
 */
@EqualsAndHashCode
@CompileStatic
class PerformanceTestScenarioDefinition {
    List<PerformanceTestsBean> performanceTests = []

    /**
     * Verify the jsonFile has same data as the instance itself
     */
    void verify(File jsonFile) {
        sort()
        PerformanceTestScenarioDefinition definitionInJson = new ObjectMapper().readValue(jsonFile, PerformanceTestScenarioDefinition).sort()
        if (this != definitionInJson) {
            File oldFile = new File(jsonFile.getAbsolutePath().replaceAll(/\.json$/, ".1.json"))
            File newFile = new File(jsonFile.getAbsolutePath().replaceAll(/\.json$/, ".2.json"))
            writeTo(oldFile)
            definitionInJson.writeTo(newFile)

            int size = Math.min(this.performanceTests.size(), definitionInJson.performanceTests.size())
            int firstDifferentElementIndex = size
            for (int i = 0; i < size; ++i) {
                if (this.performanceTests[i] != definitionInJson.performanceTests[i]) {
                    firstDifferentElementIndex = i
                    break
                }
            }
            throw new IllegalStateException("""
Scenario JSON file verification fails at index ${firstDifferentElementIndex}, please see ${oldFile.getAbsolutePath()} and ${newFile.getAbsolutePath()} for details
Old: ${firstDifferentElementIndex < performanceTests.size() ? this.performanceTests[firstDifferentElementIndex] : ""}
New: ${firstDifferentElementIndex < definitionInJson.performanceTests.size() ? definitionInJson.performanceTests[firstDifferentElementIndex] : ""}
""")
        }
    }

    @EqualsAndHashCode
    static class PerformanceTestsBean {
        /**
         * testId : org.gradle.performance.regression.java.JavaUpToDatePerformanceTest.up-to-date assemble (parallel true)
         * groups : [{"testProject":"largeJavaMultiProject","coverage":{"test":["linux","windows"]}}]
         */
        String testId
        List<GroupsBean> groups

        PerformanceTestsBean() {
        }

        PerformanceTestsBean(String testId, List<GroupsBean> groups) {
            this.testId = testId
            this.groups = groups
        }

        @Override
        String toString() {
            return "Test($testId)"
        }

        static class GroupsBean {
            /**
             * testProject : largeJavaMultiProject
             * coverage : {"test":["linux","windows"]}
             */

            String testProject

            @JsonInclude(JsonInclude.Include.NON_NULL)
            String comment

            TreeMap<String, List<String>> coverage

            boolean equals(o) {
                if (this.is(o)) {
                    return true
                }
                if (getClass() != o.class) {
                    return false
                }

                GroupsBean that = (GroupsBean) o

                if (comment != that.comment) {
                    return false
                }
                if (coverage != that.coverage) {
                    return false
                }
                if (testProject != that.testProject) {
                    return false
                }

                return true
            }

            int hashCode() {
                int result
                result = (testProject != null ? testProject.hashCode() : 0)
                result = 31 * result + (comment != null ? comment.hashCode() : 0)
                result = 31 * result + (coverage != null ? coverage.hashCode() : 0)
                return result
            }
        }
    }

    void writeTo(File file) {
        sort()
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, this)
    }

    PerformanceTestScenarioDefinition sort() {
        // Sort all fields before writing to get a deterministic result
        Collections.sort(performanceTests, { a, b -> a.testId <=> b.testId })
        performanceTests.each { Collections.sort(it.groups, { a, b -> a.testProject <=> b.testProject }) }
        return this
    }
}
