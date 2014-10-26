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
import org.gradle.util.GFileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCLoggingCollector implements DataCollector {
    private File logFile;

    public void beforeExecute(File testProjectDir, GradleExecuter executer) {
        logFile = new File(testProjectDir, "gc.txt");

        //(SF) Using '-Dorg.gradle.jvmargs' with gradle opts causes an extra vm to be forked
        //so effectively, all our performance tests run with extra forked vm
        //I think that this is ok since using jvmargs (in gradle.properties) is pretty much a standard
        executer.withGradleOpts("-Dorg.gradle.jvmargs=-verbosegc -XX:+PrintGCDetails -Xloggc:" + logFile.getAbsolutePath());
    }

    public void collect(File testProjectDir, MeasuredOperation operation) {
        collect(operation, Locale.getDefault());
    }

    public void collect(MeasuredOperation operation, Locale locale) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            try {
                collect(new WaitingReader(reader), operation, locale);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not process garbage collector log %s. File contents:\n%s", logFile, GFileUtils.readFileQuietly(logFile)), e);
        }
    }

    private void collect(WaitingReader reader, MeasuredOperation operation, Locale locale) throws IOException {
        char decimalSeparator = (new DecimalFormatSymbols(locale)).getDecimalSeparator();
        GCEventParser eventParser = new GCEventParser(decimalSeparator);
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

            GCEventParser.GCEvent event = eventParser.parseLine(line);
            if (event == GCEventParser.GCEvent.IGNORED) {
                continue;
            }

            events++;

            if (event.start < usageAtPreviousCollection) {
                throw new IllegalArgumentException("Unexpected max heap size found in garbage collection event: " + line);
            }

            totalHeapUsage += event.start - usageAtPreviousCollection;
            maxUsage = Math.max(maxUsage, event.start);
            maxUncollectedUsage = Math.max(maxUncollectedUsage, event.end);
            maxCommittedUsage = Math.max(maxCommittedUsage, event.committed);
            usageAtPreviousCollection = event.end;
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
            if (pool.toLowerCase().contains("perm gen") || pool.toLowerCase().contains("permgen")) {
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
