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

import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildExperimentRunner {
    private final DataCollector dataCollector;
    private final GradleSessionProvider executerProvider;
    private final OperationTimer timer = new OperationTimer();

    public enum Phase {
        WARMUP,
        MEASUREMENT
    }

    protected DataCollector getDataCollector() {
        return dataCollector;
    }

    protected OperationTimer getTimer() {
        return timer;
    }

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        MemoryInfoCollector memoryInfoCollector = new MemoryInfoCollector();
        memoryInfoCollector.setOutputFileName("build/totalMemoryUsed.txt");
        BuildEventTimestampCollector buildEventTimestampCollector = new BuildEventTimestampCollector("build/buildEventTimestamps.txt");
        GCLoggingCollector gcCollector = new GCLoggingCollector();
        dataCollector = new CompositeDataCollector(memoryInfoCollector, gcCollector, buildEventTimestampCollector, new CompilationLoggingCollector());
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        if (invocationSpec instanceof GradleInvocationSpec) {
            GradleInvocationSpec invocation = (GradleInvocationSpec) invocationSpec;
            File workingDirectory = invocation.getWorkingDirectory();
            final List<String> additionalJvmOpts = dataCollector.getAdditionalJvmOpts(workingDirectory);
            final List<String> additionalArgs = new ArrayList<String>(dataCollector.getAdditionalArgs(workingDirectory));
            additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());
            if (System.getProperty("org.gradle.performance.heapdump") != null) {
                additionalArgs.add("-Pheapdump");
            }

            GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
            File projectDir = buildSpec.getWorkingDirectory();
            GradleSession session = executerProvider.session(buildSpec);

            session.prepare();
            try {
                performMeasurements(session, experiment, results, projectDir);
            } finally {
                session.cleanup();
            }
        }
    }

    protected <S extends InvocationSpec, T extends InvocationCustomizer<S>> void performMeasurements(final InvocationExecutorProvider<T> session, BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir) {
        doWarmup(experiment, projectDir, session);
        waitForMillis(experiment, experiment.getSleepAfterWarmUpMillis());
        doMeasure(experiment, results, projectDir, session);
    }

    private <S extends InvocationSpec, T extends InvocationCustomizer<S>> void doMeasure(BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir, InvocationExecutorProvider<T> session) {
        int invocationCount = invocationsForExperiment(experiment);
        for (int i = 0; i < invocationCount; i++) {
            if (i > 0) {
                waitForMillis(experiment, experiment.getSleepAfterTestRoundMillis());
            }
            System.out.println();
            System.out.println(String.format("Test run #%s", i + 1));
            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.MEASUREMENT, i + 1, invocationCount);
            runOnce(session, results, info);
        }
    }

    @SuppressWarnings("unchecked")
    protected <S extends InvocationSpec, T extends InvocationCustomizer<S>> T createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
        if (info.getBuildExperimentSpec() instanceof GradleBuildExperimentSpec) {
            return Cast.uncheckedCast(new GradleInvocationCustomizer() {
                @Override
                public GradleInvocationSpec customize(GradleInvocationSpec invocationSpec) {
                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
                    GradleInvocationSpec gradleInvocationSpec = invocationSpec.withAdditionalArgs(iterationInfoArguments);
                    System.out.println("Run Gradle using JVM opts: " + gradleInvocationSpec.getJvmOpts());
                    return gradleInvocationSpec;
                }
            });
        }
        return null;
    }

    private <S extends InvocationSpec, T extends InvocationCustomizer<S>> void doWarmup(BuildExperimentSpec experiment, File projectDir, InvocationExecutorProvider<T> session) {
        int warmUpCount = warmupsForExperiment(experiment);
        for (int i = 0; i < warmUpCount; i++) {
            System.out.println();
            System.out.println(String.format("Warm-up #%s", i + 1));
            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.WARMUP, i + 1, warmUpCount);
            runOnce(session, new MeasuredOperationList(), info);
        }
    }

    protected Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        // Take more samples when using the daemon, as execution time tends to be spiky
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon()) {
                return 8;
            }
        }
        return 5;
    }

    protected int warmupsForExperiment(BuildExperimentSpec experiment) {
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        // Use more invocations to warmup when using the daemon, to allow the JVM to warm things up
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon()) {
                return 3;
            }
        }
        return 1;
    }

    // the JIT compiler seems to wait for idle period before compiling
    protected void waitForMillis(BuildExperimentSpec experiment, long sleepTimeMillis) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon() && sleepTimeMillis > 0L) {
                System.out.println();
                System.out.println(String.format("Waiting %d ms", sleepTimeMillis));
                try {
                    Thread.sleep(sleepTimeMillis);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }

    protected <S extends InvocationSpec, T extends InvocationCustomizer<S>> void runOnce(
        final InvocationExecutorProvider<T> session,
        final MeasuredOperationList results,
        final BuildExperimentInvocationInfo invocationInfo) {
        BuildExperimentSpec experiment = invocationInfo.getBuildExperimentSpec();
        final Runnable runner = session.runner(Cast.<T>uncheckedCast(this.<S, T>createInvocationCustomizer(invocationInfo)));

        if (experiment.getListener() != null) {
            experiment.getListener().beforeInvocation(invocationInfo);
        }

        MeasuredOperation operation = timer.measure(new Action<MeasuredOperation>() {
            @Override
            public void execute(MeasuredOperation measuredOperation) {
                runner.run();
            }
        });

        final AtomicBoolean omitMeasurement = new AtomicBoolean();
        if (experiment.getListener() != null) {
            experiment.getListener().afterInvocation(invocationInfo, operation, new BuildExperimentListener.MeasurementCallback() {
                @Override
                public void omitMeasurement() {
                    omitMeasurement.set(true);
                }
            });
        }

        if (!omitMeasurement.get()) {
            if (operation.getException() == null) {
                dataCollector.collect(invocationInfo, operation);
            }

            results.add(operation);
        }
    }

    protected List<String> createIterationInfoArguments(Phase phase, int iterationNumber, int iterationMax) {
        List<String> args = new ArrayList<String>(3);
        args.add("-PbuildExperimentPhase=" + phase.toString().toLowerCase());
        args.add("-PbuildExperimentIterationNumber=" + iterationNumber);
        args.add("-PbuildExperimentIterationMax=" + iterationMax);
        return args;
    }
}
