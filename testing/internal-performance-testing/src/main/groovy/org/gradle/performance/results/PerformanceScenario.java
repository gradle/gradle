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

package org.gradle.performance.results;

import java.util.Comparator;
import java.util.Objects;

public class PerformanceScenario implements Comparable<PerformanceScenario> {
    private static final Comparator<PerformanceScenario> PERFORMANCE_SCENARIO_COMPARATOR = Comparator
        .comparing(PerformanceScenario::getTestName)
        .thenComparing(PerformanceScenario::getClassName);

    private final String className;
    private final String testName;

    public PerformanceScenario(String className, String testName) {
        this.className = className;
        this.testName = testName;
    }

    public String getClassName() {
        return className;
    }

    public String getSimpleClassName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    public String getTestName() {
        return testName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PerformanceScenario that = (PerformanceScenario) o;
        return Objects.equals(className, that.className) &&
            Objects.equals(testName, that.testName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, testName);
    }

    @Override
    public String toString() {
        return "PerformanceScenario{" +
            "className='" + className + '\'' +
            ", scenario='" + testName + '\'' +
            '}';
    }

    @Override
    public int compareTo(PerformanceScenario o) {
        return PERFORMANCE_SCENARIO_COMPARATOR.compare(this, o);
    }
}
