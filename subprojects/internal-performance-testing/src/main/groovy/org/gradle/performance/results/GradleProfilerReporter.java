/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.report.AbstractGenerator;
import org.gradle.profiler.report.BenchmarkResult;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.report.Format;
import org.gradle.profiler.report.HtmlGenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

public class GradleProfilerReporter implements DataReporter<PerformanceTestResult> {
    private final OutputDirSelector outputDirSelector;
    private final CompositeReportGenerator compositeReportGenerator;
    private final BenchmarkResultCollector resultCollector;

    public GradleProfilerReporter(OutputDirSelector outputDirSelector) {
        this.outputDirSelector = outputDirSelector;
        this.compositeReportGenerator = new CompositeReportGenerator();
        this.resultCollector = new BenchmarkResultCollector(compositeReportGenerator);
    }

    @Override
    public void report(PerformanceTestResult results) {
        PerformanceExperiment experiment = results.getPerformanceExperiment();
        File baseDir = outputDirSelector.outputDirFor(experiment.getScenario().getTestName());
        baseDir.mkdirs();
        compositeReportGenerator.setGenerators(ImmutableList.of(
            new CsvGenerator(new File(baseDir, "benchmark.csv"), Format.LONG),
            new HtmlGenerator(new File(baseDir, "benchmark.html"))
        ));

        resultCollector.summarizeResults(line ->
            System.out.println("  " + line)
        );
        try {
            InvocationSettings settings = new InvocationSettings.InvocationSettingsBuilder()
                .setBenchmarkTitle(experiment.getDisplayName())
                .build();
            resultCollector.write(settings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public BenchmarkResultCollector getResultCollector() {
        return resultCollector;
    }

    @Override
    public void close() {
    }

    private static class CompositeReportGenerator extends AbstractGenerator {

        List<AbstractGenerator> generators;

        public CompositeReportGenerator() {
            super(null);
        }

        public void setGenerators(List<AbstractGenerator> generators) {
            this.generators = generators;
        }

        @Override
        public void write(InvocationSettings settings, BenchmarkResult result) throws IOException {
            for (AbstractGenerator generator : generators) {
                generator.write(settings, result);
            }
        }

        @Override
        public void summarizeResults(Consumer<String> consumer) {
            for (AbstractGenerator generator : generators) {
                generator.summarizeResults(consumer);
            }
        }

        @Override
        protected void write(InvocationSettings settings, BenchmarkResult result, BufferedWriter writer) {
            throw new UnsupportedOperationException();
        }
    }
}
