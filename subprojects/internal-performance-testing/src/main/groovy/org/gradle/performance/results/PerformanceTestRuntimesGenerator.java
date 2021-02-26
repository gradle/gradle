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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
        Map<PerformanceExperimentOnOs, Long> estimatedExperimentDurations = resultsStore.getEstimatedExperimentDurationsInMillis();
        Map<PerformanceScenario, List<Map.Entry<PerformanceExperimentOnOs, Long>>> performanceScenarioMap =
            estimatedExperimentDurations.entrySet().stream()
                .collect(Collectors.groupingBy(
                    it -> it.getKey().getPerformanceExperiment().getScenario(),
                    LinkedHashMap::new,
                    Collectors.toList())
                );
        List<PerformanceScenarioDurations> json = performanceScenarioMap.entrySet().stream()
            .map(entry -> {
                PerformanceScenario scenario = entry.getKey();
                Map<String, Map<OperatingSystem, Long>> perTestProject = entry.getValue().stream()
                    .collect(Collectors.groupingBy(
                        it -> it.getKey().getPerformanceExperiment().getTestProject(),
                        LinkedHashMap::new,
                        Collectors.toMap(it -> it.getKey().getOperatingSystem(), Map.Entry::getValue)
                    ));
                return new PerformanceScenarioDurations(
                    scenario.getClassName() + "." + scenario.getTestName(),
                    perTestProject.entrySet().stream()
                        .map(experimentEntry -> {
                                Map<OperatingSystem, Long> perOs = experimentEntry.getValue();
                                return new TestProjectDuration(
                                    experimentEntry.getKey(),
                                    perOs.get(OperatingSystem.LINUX),
                                    perOs.get(OperatingSystem.WINDOWS),
                                    perOs.get(OperatingSystem.MAC_OS)
                                );
                            }
                        )
                        .collect(Collectors.toList())
                );
            })
            .collect(Collectors.toList());

        new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(runtimesFile, json);
        Files.write(runtimesFile.toPath(), "\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

}
