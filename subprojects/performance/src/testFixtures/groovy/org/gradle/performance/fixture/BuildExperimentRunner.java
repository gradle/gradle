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
import org.gradle.internal.UncheckedException;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildExperimentRunner {
    private final GCLoggingCollector gcCollector = new GCLoggingCollector();
    private final DataCollector dataCollector;
    private final GradleSessionProvider executerProvider;
    private final OperationTimer timer = new OperationTimer();

    public enum Phase {
        WARMUP,
        MEASUREMENT
    }

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        MemoryInfoCollector memoryInfoCollector = new MemoryInfoCollector();
        memoryInfoCollector.setOutputFileName("build/totalMemoryUsed.txt");
        BuildEventTimestampCollector buildEventTimestampCollector = new BuildEventTimestampCollector("build/buildEventTimestamps.txt");
        dataCollector = new CompositeDataCollector(memoryInfoCollector, gcCollector, buildEventTimestampCollector, new CompilationLoggingCollector());
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        File workingDirectory = experiment.getInvocation().getWorkingDirectory();
        final List<String> additionalJvmOpts = dataCollector.getAdditionalJvmOpts(workingDirectory);
        final List<String> additionalArgs = new ArrayList<String>(dataCollector.getAdditionalArgs(workingDirectory));
        additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());
        if (System.getProperty("org.gradle.performance.heapdump") != null) {
            additionalArgs.add("-Pheapdump");
        }

        GradleInvocationSpec buildSpec = experiment.getInvocation().withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
        File projectDir = buildSpec.getWorkingDirectory();
        GradleSession session = executerProvider.session(buildSpec);

        session.prepare();
        try {
            int warmUpCount = warmupsForExperiment(experiment);
            for (int i = 0; i < warmUpCount; i++) {
                System.out.println();
                System.out.println(String.format("Warm-up #%s", i + 1));
                runOnce(session, experiment, new MeasuredOperationList(), projectDir, Phase.WARMUP, i + 1, warmUpCount);
            }
            waitForMillis(experiment.getSleepAfterWarmUpMillis());
            int invocationCount = invocationsForExperiment(experiment);
            for (int i = 0; i < invocationCount; i++) {
                if (i > 0) {
                    waitForMillis(experiment.getSleepAfterTestRoundMillis());
                }
                System.out.println();
                System.out.println(String.format("Test run #%s", i + 1));
                runOnce(session, experiment, results, projectDir, Phase.MEASUREMENT, i + 1, invocationCount);
            }
        } finally {
            session.cleanup();
        }
    }

    private Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        // Take more samples when using the daemon, as execution time tends to be spiky
        if (experiment.getInvocation().getUseDaemon() || experiment.getInvocation().getUseToolingApi()) {
            return 8;
        }
        return 5;
    }

    private int warmupsForExperiment(BuildExperimentSpec experiment) {
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        // Use more invocations to warmup when using the daemon, to allow the JVM to warm things up
        if (experiment.getInvocation().getUseDaemon() || experiment.getInvocation().getUseToolingApi()) {
            return 3;
        }
        return 1;
    }

    // the JIT compiler seems to wait for idle period before compiling
    private void waitForMillis(Long sleepTimeMillis) {
        if (sleepTimeMillis > 0L) {
            System.out.println();
            System.out.println(String.format("Waiting %d ms", sleepTimeMillis));
            try {
                Thread.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void runOnce(final GradleSession session, final BuildExperimentSpec experiment, MeasuredOperationList results, final File projectDir, final Phase phase, final int iterationNumber, final int iterationMax) {
        final BuildExperimentInvocationInfo invocationInfo = new BuildExperimentInvocationInfo() {
            @Override
            public BuildExperimentSpec getBuildExperimentSpec() {
                return experiment;
            }

            @Override
            public File getProjectDir() {
                return projectDir;
            }

            @Override
            public Phase getPhase() {
                return phase;
            }

            @Override
            public int getIterationNumber() {
                return iterationNumber;
            }

            @Override
            public int getIterationMax() {
                return iterationMax;
            }
        };

        final Runnable runner = session.runner(new GradleInvocationCustomizer() {
            @Override
            public GradleInvocationSpec customize(GradleInvocationSpec invocationSpec) {
                return invocationSpec.withAdditionalArgs(createIterationInfoArguments(phase, iterationNumber, iterationMax));
            }
        });

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

    private List<String> createIterationInfoArguments(Phase phase, int iterationNumber, int iterationMax) {
        List<String> args = new ArrayList<String>(3);
        args.add("-PbuildExperimentPhase=" + phase.toString().toLowerCase());
        args.add("-PbuildExperimentIterationNumber=" + iterationNumber);
        args.add("-PbuildExperimentIterationMax=" + iterationMax);
        return args;
    }
}
