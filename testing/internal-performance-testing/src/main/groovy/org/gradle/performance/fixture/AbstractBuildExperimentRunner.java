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

package org.gradle.performance.fixture;

import groovy.transform.CompileStatic;
import joptsimple.OptionParser;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.GradleProfilerReporter;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.performance.results.OutputDirSelector;
import org.gradle.performance.results.OutputDirSelectorUtil;
import org.gradle.profiler.BenchmarkResultCollector;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.ProfilerFactory;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.report.Format;
import org.gradle.profiler.result.BuildInvocationResult;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyMap;

/**
 * Runs a single build experiment.
 *
 * As part of a performance scenario, multiple experiments need to be run and compared.
 * For example for a cross-version scenario, experiments for each version will be run.
 */
@CompileStatic
public abstract class AbstractBuildExperimentRunner implements BuildExperimentRunner {
    private static final String PROFILER_KEY = "org.gradle.performance.profiler";
    private static final String PROFILER_OPTIONS_KEY = "org.gradle.performance.profiler.options";
    private static final String DEFAULT_ASYNC_PROFILER_INTERVAL_NANOS = "1000000";

    private final GradleProfilerReporter gradleProfilerReporter;
    private final Profiler profiler;
    private final OutputDirSelector outputDirSelector;

    public AbstractBuildExperimentRunner(GradleProfilerReporter gradleProfilerReporter, OutputDirSelector outputDirSelector) {
        this.outputDirSelector = outputDirSelector;
        String profilerName = System.getProperty(PROFILER_KEY);
        this.profiler = isProfilingActive() ? createProfiler(profilerName) : Profiler.NONE;
        this.gradleProfilerReporter = gradleProfilerReporter;
    }

    public static boolean isProfilingActive() {
        String profilerName = System.getProperty(PROFILER_KEY);
        return profilerName != null && !profilerName.isEmpty();
    }

    private Profiler createProfiler(String profilerName) {
        OptionParser optionParser = new OptionParser();
        optionParser.accepts("profiler");
        ProfilerFactory.configureParser(optionParser);
        ProfilerFactory profilerFactory = ProfilerFactory.of(Collections.singletonList(profilerName));
        return profilerFactory.createFromOptions(optionParser.parse(profilerOptions(profilerName)));
    }

    /* TODO(humanize) PROBE SUPPORT, NOT FOR MERGE. This method used to hand gradle-profiler an empty
       option array for everything except jprofiler, so async-profiler always ran at its defaults and
       the TeamCity profiler parameter could select a profiler but never tune one. The wall-clock
       default is a 10ms sampling interval, which gave ~29k samples over a bigCppMulti run — too
       coarse to resolve which threads are concurrently inside the compiler, which is the question
       the DefaultBuildOperationQueue worker-lease investigation actually needs answered.

       Options can now be set with -Dorg.gradle.performance.profiler.options, whitespace separated,
       forwarded to the test JVM by PerformanceTest.addExecutionParameters. async-profiler defaults to
       1ms here rather than to gradle-profiler's 10ms, so that forgetting the property costs a coarse
       profile rather than a wasted CI run. Note the finer interval is itself overhead, and overhead
       on a concurrency measurement can move the thing being measured. */
    static String[] profilerOptions(String profilerName) {
        if (profilerName.equals("jprofiler")) {
            return new String[] {"--profile", "jprofiler", "--jprofiler-home", System.getenv("JPROFILER_HOME")};
        }
        String configured = System.getProperty(PROFILER_OPTIONS_KEY);
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim().split("\\s+");
        }
        if (profilerName.startsWith("async-profiler")) {
            // Every async-profiler option is registered availableIf("profile"), so --profile has to be
            // passed alongside them or joptsimple rejects the whole command line.
            return new String[] {
                "--profile",
                profilerName,
                "--async-profiler-interval",
                DEFAULT_ASYNC_PROFILER_INTERVAL_NANOS,
                "--async-profiler-wall-interval",
                DEFAULT_ASYNC_PROFILER_INTERVAL_NANOS
            };
        }
        return new String[] {};
    }

    protected BenchmarkResultCollector getResultCollector() {
        return gradleProfilerReporter.getResultCollector();
    }

    @Override
    public void run(String testId, BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.printf("%s ...%n", experiment.getDisplayName());
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        File projectCheckoutDir = invocationSpec.getProjectCheckoutDirectory();
        projectCheckoutDir.mkdirs();
        copyTemplateTo(experiment, projectCheckoutDir);

        doRun(testId, experiment, results);
    }

    protected abstract void doRun(String testId, BuildExperimentSpec experiment, MeasuredOperationList results);

    private static void copyTemplateTo(BuildExperimentSpec experiment, File workingDir) {
        try {
            File templateDir = TestProjectLocator.findProjectDir(experiment.getProjectName());
            FileUtils.cleanDirectory(workingDir);
            FileUtils.copyDirectory(templateDir, workingDir);
        } catch (IOException ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    protected InvocationSettings.InvocationSettingsBuilder createInvocationSettingsBuilder(String testId, BuildExperimentSpec experiment) {
        File outputDir = outputDirFor(testId, experiment);
        InvocationSpec invocationSpec = experiment.getInvocation();
        return new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(invocationSpec.getProjectCheckoutDirectory())
            .setProfiler(profiler)
            .setBenchmark(true)
            .setOutputDir(outputDir)
            .setScenarioFile(null)
            .setSysProperties(emptyMap())
            .setWarmupCount(warmupsForExperiment(experiment))
            .setIterations(invocationsForExperiment(experiment))
            .setCsvFormat(Format.LONG)
            .setBuildOperationsTrace(Boolean.getBoolean("org.gradle.performance.buildOperationTrace"));
    }

    private File outputDirFor(String testId, BuildExperimentSpec spec) {
        boolean crossVersion = spec instanceof GradleBuildExperimentSpec && ((GradleBuildExperimentSpec) spec).isCrossVersion();
        File outputDir;
        if (crossVersion) {
            String version = ((GradleBuildExperimentSpec) spec).getInvocation().getGradleDistribution().getVersion().getVersion();
            outputDir = new File(outputDirSelector.outputDirFor(testId), version);
        } else {
            outputDir = new File(outputDirSelector.outputDirFor(testId), OutputDirSelectorUtil.fileSafeNameFor(spec.getDisplayName()));
        }
        outputDir.mkdirs();
        return outputDir;
    }

    private static String getExperimentOverride(String key) {
        String value = System.getProperty("org.gradle.performance.execution." + key);
        if (value != null && !"defaults".equals(value)) {
            return value;
        }
        return null;
    }

    protected static Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        String overriddenInvocationCount = getExperimentOverride("runs");
        if (overriddenInvocationCount != null) {
            return Integer.parseInt(overriddenInvocationCount);
        }
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        return 40;
    }

    protected static int warmupsForExperiment(BuildExperimentSpec experiment) {
        String overriddenWarmUpCount = getExperimentOverride("warmups");
        if (overriddenWarmUpCount != null) {
            return Integer.parseInt(overriddenWarmUpCount);
        }
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        if (usesDaemon(experiment)) {
            return 10;
        } else {
            return 1;
        }
    }

    private static boolean usesDaemon(BuildExperimentSpec experiment) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            return ((GradleInvocationSpec) invocation).getBuildWillRunInDaemon();
        }
        return false;
    }

    protected <T extends BuildInvocationResult> Consumer<T> consumerFor(
        ScenarioDefinition scenarioDefinition,
        MeasuredOperationList results,
        Consumer<T> scenarioReporter
    ) {
        AtomicInteger iterationCount = new AtomicInteger();
        return invocationResult -> {
            int currentIteration = iterationCount.incrementAndGet();
            if (currentIteration > scenarioDefinition.getWarmUpCount()) {
                MeasuredOperation measuredOperation = new MeasuredOperation();
                measuredOperation.setTotalTime(Duration.millis(invocationResult.getExecutionTime().toMillis()));
                results.add(measuredOperation);
            }
            scenarioReporter.accept(invocationResult);
        };
    }

    protected static Supplier<BuildMutator> toMutatorSupplierForSettings(InvocationSettings invocationSettings, Function<InvocationSettings, BuildMutator> mutatorFunction) {
        return () -> mutatorFunction.apply(invocationSettings);
    }
}
