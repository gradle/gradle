/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleExecuter;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCLoggingCollector implements DataCollector {
    public void beforeExecute(GradleExecuter executer) {
        executer.withGradleOpts("-verbosegc", "-XX:+PrintGCDetails", "-Xloggc:build/gc.txt");
    }

    public void collect(File testProjectDir, MeasuredOperation operation) {
        File logFile = new File(testProjectDir, "build/gc.txt");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            try {
                collect(reader, operation);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not process garbage collector log %s.", logFile), e);
        }
    }

    private void collect(BufferedReader reader, MeasuredOperation operation) throws IOException {
        Pattern gcLinePattern = Pattern.compile(".+: \\[GC");
        Pattern collectionEventPattern = Pattern.compile("\\d+\\.\\d+: \\[GC \\d+\\.\\d+: \\[.*\\] (\\d+)K->(\\d+)K\\((\\d+)K\\)");
        Pattern memoryPoolPattern = Pattern.compile("([\\w\\s]+) total (\\d+)K, used (\\d+)K \\[.+");

        long totalHeapUsage = 0;
        long maxUsage = 0;
        long maxUncollectedUsage = 0;
        long maxCommittedUsage = 0;

        // Process the garbage collection events

        long usageAtPreviousCollection = 0;
        int events = 0;

        while (true) {
            String line = reader.readLine();
            if (line == null || line.equals("Heap")) {
                break;
            }

            Matcher matcher = gcLinePattern.matcher(line);
            if (!matcher.lookingAt()) {
                continue;
            }
            events++;

            matcher = collectionEventPattern.matcher(line);
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("Unrecognized garbage collection event found in garbage collection log: " + line);
            }

            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            long committed = Long.parseLong(matcher.group(3));

            if (start < usageAtPreviousCollection) {
                throw new IllegalArgumentException("Unexpected max heap size found in garbage collection event: " + line);
            }

            totalHeapUsage += start - usageAtPreviousCollection;
            maxUsage = Math.max(maxUsage, start);
            maxUncollectedUsage = Math.max(maxUncollectedUsage, end);
            maxCommittedUsage = Math.max(maxCommittedUsage, committed);
            usageAtPreviousCollection = end;
        }

        if (events == 0) {
            throw new IllegalArgumentException("Did not find any garbage collection events in garbage collection log.");
        }

        // Process the heap usage summary at the end of the log

        long finalHeapUsage = 0;
        long finalCommittedHeap = 0;

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            Matcher matcher = memoryPoolPattern.matcher(line);
            if (!matcher.lookingAt()) {
                continue;
            }

            String pool = matcher.group(1).trim();
            if (pool.contains("perm gen")) {
                continue;
            }

            long committed = Long.parseLong(matcher.group(2));
            long usage = Long.parseLong(matcher.group(3));

            finalHeapUsage += usage;
            finalCommittedHeap += committed;
        }

        if (finalHeapUsage == 0) {
            throw new IllegalArgumentException("Did not find any memory pool usage details in garbage collection log.");
        }

        if (finalHeapUsage < usageAtPreviousCollection) {
            throw new IllegalArgumentException("Unexpected max heap size found in memory pool usage.");
        }

        totalHeapUsage += finalHeapUsage - usageAtPreviousCollection;
        maxUsage = Math.max(maxUsage, finalHeapUsage);
        maxCommittedUsage = Math.max(maxCommittedUsage, finalCommittedHeap);

        operation.setTotalHeapUsage(DataAmount.kbytes(BigDecimal.valueOf(totalHeapUsage)));
        operation.setMaxHeapUsage(DataAmount.kbytes(BigDecimal.valueOf(maxUsage)));
        operation.setMaxUncollectedHeap(DataAmount.kbytes(BigDecimal.valueOf(maxUncollectedUsage)));
        operation.setMaxCommittedHeap(DataAmount.kbytes(BigDecimal.valueOf(maxCommittedUsage)));
    }
}
