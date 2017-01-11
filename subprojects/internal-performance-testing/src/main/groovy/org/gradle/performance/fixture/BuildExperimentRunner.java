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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildExperimentRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildExperimentRunner.class);

    private final DataCollector dataCollector;
    private final GradleSessionProvider executerProvider;
    private final HonestProfilerCollector honestProfiler;

    public enum Phase {
        WARMUP,
        MEASUREMENT
    }

    protected DataCollector getDataCollector() {
        return dataCollector;
    }

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        honestProfiler = new HonestProfilerCollector();
        dataCollector = new CompositeDataCollector(honestProfiler);
    }

    public HonestProfilerCollector getHonestProfiler() {
        return honestProfiler;
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        if (invocationSpec instanceof GradleInvocationSpec) {
            GradleInvocationSpec invocation = (GradleInvocationSpec) invocationSpec;
            honestProfiler.setInitiallyStopped(invocation.getUseDaemon());
            File workingDirectory = invocation.getWorkingDirectory();
            workingDirectory.mkdirs();
            final List<String> additionalJvmOpts = dataCollector.getAdditionalJvmOpts(workingDirectory);
            final List<String> additionalArgs = new ArrayList<String>(dataCollector.getAdditionalArgs(workingDirectory));
            additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());

            GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
            copyTemplateTo(experiment, workingDirectory);
            GradleSession session = executerProvider.session(buildSpec);

            session.prepare();
            try {
                performMeasurements(session, experiment, results, workingDirectory);
            } finally {
                session.cleanup();
                CompositeStoppable.stoppable(dataCollector).stop();
            }
        }
    }

    private void copyTemplateTo(BuildExperimentSpec experiment, File workingDir) {
        File templateDir = new TestProjectLocator().findProjectDir(experiment.getProjectName());
        GFileUtils.cleanDirectory(workingDir);
        GFileUtils.copyDirectory(templateDir, workingDir);
    }

    protected void performMeasurements(final InvocationExecutorProvider session, BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir) {
        doWarmup(experiment, projectDir, session);
        doMeasure(experiment, results, projectDir, session);
    }

    private void doMeasure(BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir, InvocationExecutorProvider session) {
        int invocationCount = invocationsForExperiment(experiment);
        for (int i = 0; i < invocationCount; i++) {
            System.out.println();
            System.out.println(String.format("Test run #%s", i + 1));
            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.MEASUREMENT, i + 1, invocationCount);
            runOnce(session, results, info);
        }
    }

    @SuppressWarnings("unchecked")
    protected InvocationCustomizer createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
        if (info.getBuildExperimentSpec() instanceof GradleBuildExperimentSpec) {
            return new InvocationCustomizer() {
                @Override
                public <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo info, T invocationSpec) {
                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
                    GradleInvocationSpec gradleInvocationSpec = ((GradleInvocationSpec) invocationSpec).withAdditionalArgs(iterationInfoArguments);
                    System.out.println("Run Gradle using JVM opts: " + gradleInvocationSpec.getJvmOpts());
                    if(info.getBuildExperimentSpec().getInvocationCustomizer() != null) {
                        gradleInvocationSpec = info.getBuildExperimentSpec().getInvocationCustomizer().customize(info, gradleInvocationSpec);
                    }
                    return (T) gradleInvocationSpec;
                }
            };
        }
        return null;
    }

    private void doWarmup(BuildExperimentSpec experiment, File projectDir, InvocationExecutorProvider session) {
        int warmUpCount = warmupsForExperiment(experiment);
        for (int i = 0; i < warmUpCount; i++) {
            System.out.println();
            System.out.println(String.format("Warm-up #%s", i + 1));
            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.WARMUP, i + 1, warmUpCount);
            runOnce(session, new MeasuredOperationList(), info);
        }
    }

    private static String getExperimentOverride(String key) {
        String value = System.getProperty("org.gradle.performance.execution." + key);
        if (value != null && !"defaults".equals(value)) {
            return value;
        }
        return null;
    }

    protected Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        String overridenInvocationCount = getExperimentOverride("runs");
        if (overridenInvocationCount != null) {
            return Integer.valueOf(overridenInvocationCount);
        }
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        return 20;
    }

    protected int warmupsForExperiment(BuildExperimentSpec experiment) {
        String overridenWarmUpCount = getExperimentOverride("warmups");
        if (overridenWarmUpCount != null) {
            return Integer.valueOf(overridenWarmUpCount);
        }
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        if (usesDaemon(experiment)) {
            return 20;
        } else {
            return 1;
        }
    }

    private boolean usesDaemon(BuildExperimentSpec experiment) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon()) {
                return true;
            }
        }
        return false;
    }

    protected void runOnce(
            final InvocationExecutorProvider session,
        final MeasuredOperationList results,
        final BuildExperimentInvocationInfo invocationInfo) {
        BuildExperimentSpec experiment = invocationInfo.getBuildExperimentSpec();
        final Action<MeasuredOperation> runner = session.runner(invocationInfo, wrapInvocationCustomizer(invocationInfo, createInvocationCustomizer(invocationInfo)));

        if (experiment.getListener() != null) {
            experiment.getListener().beforeInvocation(invocationInfo);
        }

        MeasuredOperation operation = new MeasuredOperation();
        try {
            runner.execute(operation);
        } catch (Exception e) {
            operation.setException(e);
        }

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
                if (operation.isValid()) {
                    results.add(operation);
                } else {
                    LOGGER.error("Discarding invalid operation record {}", operation);
                }
            } else {
                LOGGER.error("Discarding invalid operation record " + operation, operation.getException());
            }
        }
    }

    private InvocationCustomizer wrapInvocationCustomizer(BuildExperimentInvocationInfo invocationInfo, final InvocationCustomizer invocationCustomizer) {
        final InvocationCustomizer experimentSpecificCustomizer = invocationInfo.getBuildExperimentSpec().getInvocationCustomizer();
        if (experimentSpecificCustomizer != null) {
            if (invocationCustomizer != null) {
                return new InvocationCustomizer() {
                    @Override
                    public <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo info, T invocationSpec) {
                        return experimentSpecificCustomizer.customize(info, invocationCustomizer.customize(info, invocationSpec));
                    }
                };
            } else {
                return experimentSpecificCustomizer;
            }
        } else {
            return invocationCustomizer;
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
