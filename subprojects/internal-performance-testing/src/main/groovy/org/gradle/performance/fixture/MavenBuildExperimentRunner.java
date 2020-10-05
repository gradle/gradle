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
import groovy.transform.CompileStatic;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.results.GradleProfilerReporter;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.BuildInvoker;
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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@CompileStatic
public class MavenBuildExperimentRunner extends AbstractBuildExperimentRunner {
    public MavenBuildExperimentRunner(GradleProfilerReporter gradleProfilerReporter) {
        super(gradleProfilerReporter);
    }

    @Override
    public void doRun(BuildExperimentSpec experiment, MeasuredOperationList results) {
        MavenBuildExperimentSpec experimentSpec = (MavenBuildExperimentSpec) experiment;

        MavenInvocationSpec invocationSpec = experimentSpec.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();

        InvocationSettings invocationSettings = createInvocationSettings(experimentSpec);
        MavenScenarioDefinition scenarioDefinition = createScenarioDefinition(experimentSpec, invocationSettings);

        try {
            MavenScenarioInvoker scenarioInvoker = new MavenScenarioInvoker();
            AtomicInteger iterationCount = new AtomicInteger(0);
            Logging.setupLogging(workingDirectory);

            Consumer<BuildInvocationResult> scenarioReporter = getResultCollector(scenarioDefinition.getName()).scenario(
                scenarioDefinition,
                scenarioInvoker.samplesFor(invocationSettings, scenarioDefinition)
            );
            scenarioInvoker.run(scenarioDefinition, invocationSettings, new BenchmarkResultCollector() {
                @Override
                public <S extends ScenarioDefinition, T extends BuildInvocationResult> Consumer<T> scenario(S scenario, List<Sample<? super T>> samples) {
                    return (Consumer<T>) consumerFor(scenarioDefinition, iterationCount, results, scenarioReporter);
                }
            });
            getFlameGraphGenerator().generateDifferentialGraphs(experiment);
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
        File outputDir = getFlameGraphGenerator().getJfrOutputDirectory(experimentSpec);
        return new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(experimentSpec.getInvocation().getWorkingDirectory())
            .setProfiler(getProfiler())
            .setBenchmark(true)
            .setOutputDir(outputDir)
            .setInvoker(BuildInvoker.Maven)
            .setVersions(ImmutableList.of(experimentSpec.getInvocation().getMavenVersion()))
            .setTargets(experimentSpec.getInvocation().getTasksToRun())
            .setSysProperties(emptyMap())
            .setWarmupCount(warmupsForExperiment(experimentSpec))
            .setIterations(invocationsForExperiment(experimentSpec))
            .setMeasuredBuildOperations(emptyList())
            .setCsvFormat(CsvGenerator.Format.LONG)
            .build();
    }

    private MavenScenarioDefinition createScenarioDefinition(MavenBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings) {
        MavenInvocationSpec invocation = experimentSpec.getInvocation();
        List<String> arguments = ImmutableList.<String>builder()
            .addAll(invocation.getTasksToRun())
            .addAll(invocation.getArgs())
            .build();
        return new MavenScenarioDefinition(
            experimentSpec.getDisplayName(),
            experimentSpec.getDisplayName(),
            arguments,
            experimentSpec.getBuildMutators().stream()
                .map(mutatorFunction -> mutatorFunction.apply(invocationSettings))
                .collect(Collectors.toList()),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir(),
            experimentSpec.getInvocation().getInstallation().getHome()
        );
    }
}
