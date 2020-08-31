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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.BuildInvoker;
import org.gradle.profiler.BuildMutatorFactory;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.MavenScenarioDefinition;
import org.gradle.profiler.MavenScenarioInvoker;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.result.Sample;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GradleVsMavenBuildExperimentRunner extends GradleProfilerBuildExperimentRunner {
    public GradleVsMavenBuildExperimentRunner(BenchmarkResultCollector resultCollector) {
        super(resultCollector);
    }

    @Override
    public void doRun(BuildExperimentSpec experiment, MeasuredOperationList results) {
        if (experiment instanceof MavenBuildExperimentSpec) {
            runMavenExperiment((MavenBuildExperimentSpec) experiment, results);
        } else {
            super.doRun(experiment, results);
        }
    }

    private void runMavenExperiment(MavenBuildExperimentSpec experimentSpec, MeasuredOperationList results) {
        MavenInvocationSpec invocationSpec = experimentSpec.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();

        InvocationSettings invocationSettings = createInvocationSettings(experimentSpec);
        MavenScenarioDefinition scenarioDefinition = createScenarioDefinition(experimentSpec, invocationSettings);

        try {
            MavenScenarioInvoker scenarioInvoker = new MavenScenarioInvoker();
            AtomicInteger iterationCount = new AtomicInteger(0);
            int warmUpCount = scenarioDefinition.getWarmUpCount();
            Logging.setupLogging(workingDirectory);

            Consumer<BuildInvocationResult> scenarioReporter = resultCollector.scenario(
                scenarioDefinition,
                ImmutableList.<Sample<? super BuildInvocationResult>>builder()
                    .add(BuildInvocationResult.EXECUTION_TIME)
                    .build()
            );
            scenarioInvoker.run(scenarioDefinition, invocationSettings, new BenchmarkResultCollector() {
                @Override
                public <T extends BuildInvocationResult> Consumer<T> scenario(ScenarioDefinition scenario, List<Sample<? super T>> samples) {
                    return invocationResult -> {
                        int currentIteration = iterationCount.incrementAndGet();
                        if (currentIteration > warmUpCount) {
                            MeasuredOperation measuredOperation = new MeasuredOperation();
                            measuredOperation.setTotalTime(Duration.millis(invocationResult.getExecutionTime().toMillis()));
                            results.add(measuredOperation);
                        }
                        scenarioReporter.accept(invocationResult);
                    };
                }
            });
            flameGraphGenerator.generateGraphs(experimentSpec);
            flameGraphGenerator.generateDifferentialGraphs();
        } catch (IOException | InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                Logging.resetLogging();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private InvocationSettings createInvocationSettings(MavenBuildExperimentSpec experimentSpec) {
        File outputDir = flameGraphGenerator.getJfrOutputDirectory(experimentSpec);
        return new InvocationSettings(
            experimentSpec.getInvocation().getWorkingDirectory(),
            profiler,
            true,
            outputDir,
            new BuildInvoker() {
                @Override
                public String toString() {
                    return "Maven";
                }
            },
            false,
            null,
            ImmutableList.of(experimentSpec.getInvocation().getMavenVersion()),
            experimentSpec.getInvocation().getTasksToRun(),
            ImmutableMap.of(),
            null,
            warmupsForExperiment(experimentSpec),
            invocationsForExperiment(experimentSpec),
            false,
            ImmutableList.of(),
            CsvGenerator.Format.LONG);
    }

    private MavenScenarioDefinition createScenarioDefinition(MavenBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings) {
        return new MavenScenarioDefinition(
            experimentSpec.getDisplayName(),
            experimentSpec.getDisplayName(),
            experimentSpec.getInvocation().getTasksToRun(),
            new BuildMutatorFactory(experimentSpec.getBuildMutators().stream()
                .map(mutatorFunction -> toMutatorSupplierForSettings(invocationSettings, mutatorFunction))
                .collect(Collectors.toList())
            ),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir()
        );
    }
}
