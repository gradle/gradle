/*
 * Copyright 2013 the original author or authors.
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

import groovy.transform.CompileStatic;
import org.gradle.performance.results.CrossVersionPerformanceResults;
import org.gradle.performance.results.DataReporter;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CompileStatic
public class CompositeDataReporter implements DataReporter<CrossVersionPerformanceResults>, Closeable {
    private final List<DataReporter<CrossVersionPerformanceResults>> reporters;
    private final Set<String> testIds = new HashSet<>();

    public CompositeDataReporter(List<DataReporter<CrossVersionPerformanceResults>> reporters) {
        this.reporters = reporters;
    }

    @Override
    public void report(CrossVersionPerformanceResults results) {
        if (!testIds.add(results.getTestId())) {
            throw new IllegalArgumentException(String.format("Multiple performance test executions with id '%s' found.", results.getTestId()));
        }
        for (DataReporter<CrossVersionPerformanceResults> reporter : reporters) {
            reporter.report(results);
        }
    }

    @Override
    public void close() throws IOException {
        for (DataReporter<CrossVersionPerformanceResults> reporter : reporters) {
            reporter.close();
        }
    }
}
