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

package org.gradle.testing;

import org.gradle.api.internal.tasks.options.Option;

/**
 * A test that checks execution time and memory consumption.
 */
public class PerformanceTest extends DistributionTest {

    @Option(option = "scenarios", description = "A semicolon-separated list of performance test scenario ids to run.")
    public void setScenarios(String scenarios) {
        systemProperty("org.gradle.performance.scenarios", scenarios);
    }

    @Option(option = "baselines", description = "A comma or semicolon separated list of Gradle versions to be used as baselines for comparing.")
    public void setBaselines(String baselines) {
        systemProperty("org.gradle.performance.baselines", baselines);
    }

    @Option(option = "warmups", description = "Number of warmups before measurements")
    public void setWarmups(String warmups) {
        systemProperty("org.gradle.performance.execution.warmups", warmups);
    }

    @Option(option = "runs", description = "Number of iterations of measurements")
    public void setRuns(String runs) {
        systemProperty("org.gradle.performance.execution.runs", runs);
    }

    @Option(option = "checks", description = "Tells which regressions to check. One of [none, speed, memory, all]")
    public void setChecks(String checks) {
        systemProperty("org.gradle.performance.execution.checks", checks);
    }
}
