/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.UncheckedIOException;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.*;
import java.util.Collections;
import java.util.List;

public class BuildEventTimestampCollector implements DataCollector {

    private final String outputFile;

    public BuildEventTimestampCollector(String outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public List<String> getAdditionalJvmOpts(File workingDir) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getAdditionalArgs(File workingDir) {
        return ImmutableList.of("--init-script=init.gradle");
    }

    @Override
    public void collect(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation) {
        final File timestampFile = new File(invocationInfo.getProjectDir(), outputFile);
        final String absolutePath = timestampFile.getAbsolutePath();
        if (!timestampFile.exists()) {
            throw new IllegalStateException(String.format("Could not find %s. Cannot collect build event timestamps.", absolutePath));
        }
        List<String> lines = readLines(timestampFile);
        if (lines.size() < 3) {
            throw new IllegalStateException(String.format("Build event timestamp log at %s should contain at least 3 lines.", absolutePath));
        }
        List<Long> timestamps = parseTimestamps(absolutePath, lines);
        operation.setConfigurationTime(Duration.millis((timestamps.get(1) - timestamps.get(0)) / 1000000L));
        operation.setExecutionTime(Duration.millis((timestamps.get(2) - timestamps.get(1)) / 1000000L));
    }

    private List<Long> parseTimestamps(final String absolutePath, List<String> lines) {
        try {
            return ImmutableList.of(Long.valueOf(lines.get(0)), Long.valueOf(lines.get(1)), Long.valueOf(lines.get(2)));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("One of the timestamps in build event timestamp log at %s is not valid.", absolutePath), e);
        }
    }

    private List<String> readLines(File timestampFile) {
        ImmutableList.Builder<String> lines = ImmutableList.builder();
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(timestampFile);
            BufferedReader reader = new BufferedReader(fileReader);
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines.build();
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
