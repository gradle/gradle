/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.reporting;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects method interception reports and prints them to the console at the end of the build.
 */
public class DefaultMethodInterceptionReportCollector implements MethodInterceptionReportCollector, Closeable {

    private final List<File> reports;

    public DefaultMethodInterceptionReportCollector() {
        this.reports = new ArrayList<>();
    }

    @Override
    public void collect(File report) {
        reports.add(report);
    }

    @Override
    public void close() {
        if (reports.stream().mapToLong(File::length).sum() > 0) {
            System.out.println("\nIntercepted methods:");
            reports.stream().flatMap(report -> {
                try {
                    return Files.readAllLines(report.toPath(), StandardCharsets.UTF_8).stream();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).distinct().forEach(System.out::println);
        }
        reports.clear();
    }
}
