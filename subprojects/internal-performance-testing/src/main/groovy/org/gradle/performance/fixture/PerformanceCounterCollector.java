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

package org.gradle.performance.fixture;

import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PerformanceCounterCollector implements DataCollector {

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        try {
            doCollect(invocationInfo, operation);
        } catch (IOException e) {
            // exception in parsing input, ignore
            System.err.println("Exception in parsing input. " + e.getClass().getName() + " " + e.getMessage());
        }
    }

    private void doCollect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) throws IOException {
        File buildDir = new File(invocationInfo.getProjectDir(), "build");
        File performanceCounterFileStart = new File(buildDir, "perf_counters_start.txt");
        File performanceCounterFileFinish = new File(buildDir, "perf_counters_finish.txt");
        if (performanceCounterFileStart.exists() && performanceCounterFileFinish.exists()) {
            PerformanceCounterFile startCounters = new PerformanceCounterFile(performanceCounterFileStart);
            PerformanceCounterFile finishCounters = new PerformanceCounterFile(performanceCounterFileFinish);

            long compileTotalTimeMs = finishCounters.tickToMs(finishCounters.getCompileTotalTimeTicks() - startCounters.getCompileTotalTimeTicks());
            operation.setCompileTotalTime(Duration.millis(compileTotalTimeMs));

            long gcTotalTimeMs = finishCounters.tickToMs(finishCounters.getGcTotalTimeTicks() - startCounters.getGcTotalTimeTicks());
            operation.setGcTotalTime(Duration.millis(gcTotalTimeMs));
        }
    }

    static class PerformanceCounterFile {
        private static final String KEY_TICK_FREQUENCY = "sun.os.hrt.frequency";
        private static final String KEY_TOTAL_COMPILE_TIME = "java.ci.totalTime";
        private static final String KEY_YOUNG_GEN_GC_TIME = "sun.gc.collector.0.time";
        private static final String KEY_OLD_GEN_GC_TIME = "sun.gc.collector.1.time";
        private static final Set<String> USED_KEYS = new HashSet<String>(Arrays.asList(KEY_TICK_FREQUENCY, KEY_TOTAL_COMPILE_TIME, KEY_YOUNG_GEN_GC_TIME, KEY_OLD_GEN_GC_TIME));

        private final long compileTotalTimeTicks;
        private final long gcTotalTimeTicks;
        private final long tickToMsDivisor;

        PerformanceCounterFile(File file) throws IOException {
            Map<String, String> parsedValues = parseFile(file);
            long tickFrequency = getLongValue(parsedValues, KEY_TICK_FREQUENCY);
            tickToMsDivisor = tickFrequency / TimeUnit.SECONDS.toMillis(1);

            compileTotalTimeTicks = getLongValue(parsedValues, KEY_TOTAL_COMPILE_TIME);
            gcTotalTimeTicks = getLongValue(parsedValues, KEY_YOUNG_GEN_GC_TIME) + getLongValue(parsedValues, KEY_OLD_GEN_GC_TIME);
        }

        private Long getLongValue(Map<String, String> parsedValues, String key) {
            return Long.valueOf(parsedValues.get(key));
        }

        private Map<String, String> parseFile(File file) throws IOException {
            Map<String, String> parsedValues = new HashMap<String, String>();
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0];
                    String value = parts[1];
                    if (USED_KEYS.contains(key)) {
                        parsedValues.put(key, value);
                    }
                }
            } finally {
                reader.close();
            }
            return parsedValues;
        }

        public long getCompileTotalTimeTicks() {
            return compileTotalTimeTicks;
        }

        public long getGcTotalTimeTicks() {
            return gcTotalTimeTicks;
        }

        public long tickToMs(long ticks) {
            return ticks / tickToMsDivisor;
        }
    }
}
