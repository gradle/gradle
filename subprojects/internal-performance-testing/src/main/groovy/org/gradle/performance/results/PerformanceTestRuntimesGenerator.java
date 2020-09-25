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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PerformanceTestRuntimesGenerator {

    public static void main(String[] args) throws IOException {
        new PerformanceTestRuntimesGenerator().generate(new File(args[0]));
    }

    public void generate(File runtimesFile) throws IOException {
        AllResultsStore resultsStore = new AllResultsStore();
        Map<PerformanceExperiment, Long> estimatedExperimentTimes = resultsStore.getEstimatedExperimentTimes(OperatingSystem.LINUX);
        Map<PerformanceScenario, List<Map.Entry<PerformanceExperiment, Long>>> performanceScenarioMap =
            estimatedExperimentTimes.entrySet().stream()
                .collect(Collectors.groupingBy(
                    it -> it.getKey().getScenario(),
                    LinkedHashMap::new,
                    Collectors.toList())
                );
        List<Map<String, Object>> json = performanceScenarioMap.entrySet().stream()
            .map(entry -> {
                PerformanceScenario scenario = entry.getKey();
                List<Map.Entry<PerformanceExperiment, Long>> times = entry.getValue();
                return ImmutableMap.of(
                    "scenario", scenario.getClassName() + "." + scenario.getTestName(),
                    "runtimes", times.stream()
                        .map(experimentEntry -> ImmutableMap.of(
                            "testProject", experimentEntry.getKey().getTestProject(),
                            "linux", experimentEntry.getValue()
                        ))
                        .collect(Collectors.toList())
                );
            })
            .collect(Collectors.toList());

        new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(runtimesFile, json);
    }

}
