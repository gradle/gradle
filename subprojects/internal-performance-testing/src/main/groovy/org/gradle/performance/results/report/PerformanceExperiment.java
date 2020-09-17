/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.performance.results.report;

import java.util.Objects;

public class PerformanceExperiment {
    private final String testProject;
    private final String scenario;

    public PerformanceExperiment(String testProject, String scenario) {
        this.testProject = testProject;
        this.scenario = scenario;
    }

    public String getTestProject() {
        return testProject;
    }

    public String getScenario() {
        return scenario;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PerformanceExperiment that = (PerformanceExperiment) o;
        return testProject.equals(that.testProject) &&
            scenario.equals(that.scenario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testProject, scenario);
    }
}
