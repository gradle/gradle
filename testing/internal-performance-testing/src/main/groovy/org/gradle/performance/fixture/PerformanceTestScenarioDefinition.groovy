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

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @EqualsAndHashCode
        static class GroupsBean {
            /**
             * testProject : largeJavaMultiProject
             * coverage : {"test":["linux","windows"]}
             */
            String testProject

            String comment

            TreeMap<String, List<String>> coverage
        }
    }

    void writeTo(File file) {
        sort()
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file, this)
        file.append('\n')
    }

    PerformanceTestScenarioDefinition sort() {
        // Sort all fields before writing to get a deterministic result
        Collections.sort(performanceTests, { a, b -> a.testId <=> b.testId })
        performanceTests.each { Collections.sort(it.groups, { a, b -> a.testProject <=> b.testProject }) }
        return this
    }
}
