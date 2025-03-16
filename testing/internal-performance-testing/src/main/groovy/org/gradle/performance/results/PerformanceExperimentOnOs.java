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

import java.util.Objects;

public class PerformanceExperimentOnOs {
    private final PerformanceExperiment performanceExperiment;
    private final OperatingSystem operatingSystem;

    public PerformanceExperimentOnOs(PerformanceExperiment performanceExperiment, OperatingSystem operatingSystem) {
        this.performanceExperiment = performanceExperiment;
        this.operatingSystem = operatingSystem;
    }

    public PerformanceExperiment getPerformanceExperiment() {
        return performanceExperiment;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PerformanceExperimentOnOs that = (PerformanceExperimentOnOs) o;
        return performanceExperiment.equals(that.performanceExperiment) &&
            operatingSystem == that.operatingSystem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(performanceExperiment, operatingSystem);
    }

    @Override
    public String toString() {
        return "PerformanceExperimentOnOs{" +
            "performanceExperiment=" + performanceExperiment +
            ", operatingSystem=" + operatingSystem +
            '}';
    }
}
