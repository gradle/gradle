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

import com.google.common.collect.ImmutableList;
import groovy.transform.CompileStatic;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.performance.results.GradleProfilerReporter;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.performance.results.OutputDirSelector;
import org.gradle.performance.results.OutputDirSelectorUtil;
import org.gradle.profiler.BuildAction;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.BuildMutator;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.ScenarioInvoker;
import org.gradle.profiler.gradle.CrossVersionGradleScenarioInvoker;
import org.gradle.profiler.gradle.DaemonControl;
import org.gradle.profiler.gradle.GradleBuildInvocationResult;
import org.gradle.profiler.gradle.GradleBuildInvoker;
import org.gradle.profiler.gradle.GradleScenarioDefinition;
import org.gradle.profiler.gradle.GradleScenarioInvoker;
import org.gradle.profiler.gradle.RunTasksAction;
import org.gradle.profiler.ide.IdeConfiguration;
import org.gradle.profiler.ide.IdeType;
import org.gradle.profiler.ide.invoker.IdeGradleScenarioDefinition;
import org.gradle.profiler.ide.invoker.IdeGradleScenarioInvoker;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * {@inheritDoc}
 *
 * This runner uses Gradle profiler to execute the actual experiment.
 */
@CompileStatic
public class GradleBuildExperimentRunner extends AbstractBuildExperimentRunner {

    private final PidInstrumentation pidInstrumentation;
    private final IntegrationTestBuildContext context = new IntegrationTestBuildContext();

    public GradleBuildExperimentRunner(GradleProfilerReporter gradleProfilerReporter, OutputDirSelector outputDirSelector) {
        super(gradleProfilerReporter, outputDirSelector);
        try {
            this.pidInstrumentation = new PidInstrumentation();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void doRun(String testId, BuildExperimentSpec experiment, MeasuredOperationList results) {
        InvocationSpec invocationSpec = experiment.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();

        if (!(invocationSpec instanceof GradleInvocationSpec invocation)) {
            throw new IllegalArgumentException("Can only run Gradle invocations with Gradle profiler");
        }

        List<String> additionalJvmOpts = new ArrayList<>();
        List<String> additionalArgs = new ArrayList<>();
        additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());

        GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);

        GradleBuildExperimentSpec gradleExperiment = (GradleBuildExperimentSpec) experiment;
        InvocationSettings invocationSettings = createInvocationSettings(testId, buildSpec, gradleExperiment);
        GradleScenarioDefinition scenarioDefinition = createScenarioDefinition(gradleExperiment, invocationSettings, invocation);

        try {
            GradleScenarioInvoker scenarioInvoker = createScenarioInvoker(invocationSettings.getGradleUserHome());
            Logging.setupLogging(workingDirectory);
            if (buildSpec.isUseAndroidStudio()) {
                IdeGradleScenarioDefinition studioScenarioDefinition = new IdeGradleScenarioDefinition(
                    scenarioDefinition,
                    IdeType.ANDROID_STUDIO,
                    buildSpec.getStudioJvmArgs(),
                    buildSpec.getStudioIdeaProperties()
                );
                IdeGradleScenarioInvoker studioScenarioInvoker = new IdeGradleScenarioInvoker(scenarioInvoker);
                doRunScenario(studioScenarioDefinition, studioScenarioInvoker, invocationSettings, results);
            } else {
                if (gradleExperiment.getInvocation().isUseToolingApi()) {
                    initializeNativeServicesForTapiClient(buildSpec, scenarioDefinition);
                }
                doRunScenario(scenarioDefinition, scenarioInvoker, invocationSettings, results);
            }
        } catch (IOException | InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                Logging.resetLogging();
            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
            ConnectorServices.reset();
        }
    }

    private <S extends ScenarioDefinition, R extends BuildInvocationResult> void doRunScenario(
        S scenarioDefinition,
        ScenarioInvoker<S, R> scenarioInvoker,
        InvocationSettings invocationSettings,
        MeasuredOperationList results
    ) throws IOException, InterruptedException {
        Consumer<R> scenarioReporter = getResultCollector().scenario(
            scenarioDefinition,
            scenarioInvoker.samplesFor(invocationSettings, scenarioDefinition)
        );
        Consumer<R> resultConsumer = consumerFor(scenarioDefinition, results, scenarioReporter);
        scenarioInvoker.run(scenarioDefinition, invocationSettings, resultConsumer);
    }

    /**
     * Runs the current spec and one or more baseline specs as a single interleaved cross-version benchmark,
     * keeping both Gradle daemons alive throughout so each measure on A is immediately followed by a measure on B.
     *
     * <p>For pilot scope, this supports only the standard {@link GradleScenarioInvoker} path: no Android Studio,
     * and all specs must declare equal warmup/invocation counts.
     */
    public void runInterleavedCrossVersion(
        String testId,
        BuildExperimentSpec currentExperiment, MeasuredOperationList currentResults,
        List<? extends BuildExperimentSpec> baselineExperiments, java.util.Map<? extends BuildExperimentSpec, MeasuredOperationList> baselineResults
    ) {
        if (baselineExperiments.isEmpty()) {
            throw new IllegalArgumentException("Interleaved cross-version run requires at least one baseline");
        }
        if (baselineExperiments.size() > 1) {
            // For the pilot, support exactly one baseline. Multiple baselines would need n-way interleaving.
            throw new IllegalArgumentException("Interleaved cross-version run currently supports a single baseline; got " + baselineExperiments.size());
        }
        BuildExperimentSpec baselineExperiment = baselineExperiments.get(0);
        MeasuredOperationList baselineResultsList = baselineResults.get(baselineExperiment);

        // Android Studio scenarios need the IdeGradleScenarioDefinition / IdeGradleScenarioInvoker wrapping
        // that GradleBuildExperimentRunner.doRun() performs. The interleaved invoker does not currently
        // implement that wrapping, so fall back to the original sequential execution for these scenarios.
        if (usesAndroidStudio(currentExperiment) || usesAndroidStudio(baselineExperiment)) {
            run(testId, currentExperiment, currentResults);
            run(testId, baselineExperiment, baselineResultsList);
            return;
        }

        prepareWorkingDirectory(currentExperiment);
        prepareWorkingDirectory(baselineExperiment);

        ScenarioBundle current = prepareScenario(testId, currentExperiment);
        ScenarioBundle baseline = prepareScenario(testId, baselineExperiment);

        File workingDirectory = currentExperiment.getInvocation().getWorkingDirectory();
        try {
            Logging.setupLogging(workingDirectory);
            if (currentExperiment.getInvocation() instanceof GradleInvocationSpec gradleSpec && gradleSpec.isUseToolingApi()) {
                initializeNativeServicesForTapiClient(gradleSpec, current.scenarioDefinition);
            }

            Consumer<GradleBuildInvocationResult> currentReporter = getResultCollector().scenario(current.scenarioDefinition, current.invoker.samplesFor(current.invocationSettings, current.scenarioDefinition));
            Consumer<GradleBuildInvocationResult> baselineReporter = getResultCollector().scenario(baseline.scenarioDefinition, baseline.invoker.samplesFor(baseline.invocationSettings, baseline.scenarioDefinition));

            Consumer<GradleBuildInvocationResult> currentConsumer = consumerFor(current.scenarioDefinition, currentResults, currentReporter);
            Consumer<GradleBuildInvocationResult> baselineConsumer = consumerFor(baseline.scenarioDefinition, baselineResultsList, baselineReporter);

            CrossVersionGradleScenarioInvoker interleavedInvoker = new CrossVersionGradleScenarioInvoker(pidInstrumentation);
            interleavedInvoker.run(
                current.scenarioDefinition, current.invocationSettings, currentConsumer,
                baseline.scenarioDefinition, baseline.invocationSettings, baselineConsumer
            );
        } catch (IOException | InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                Logging.resetLogging();
            } catch (IOException e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
            ConnectorServices.reset();
        }
    }

    private static boolean usesAndroidStudio(BuildExperimentSpec experiment) {
        return experiment.getInvocation() instanceof GradleInvocationSpec gradleSpec && gradleSpec.isUseAndroidStudio();
    }

    private ScenarioBundle prepareScenario(String testId, BuildExperimentSpec experiment) {
        InvocationSpec invocationSpec = experiment.getInvocation();
        if (!(invocationSpec instanceof GradleInvocationSpec invocation)) {
            throw new IllegalArgumentException("Interleaved cross-version only supports Gradle invocations");
        }
        List<String> additionalJvmOpts = new ArrayList<>();
        List<String> additionalArgs = new ArrayList<>();
        additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());
        GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
        GradleBuildExperimentSpec gradleExperiment = (GradleBuildExperimentSpec) experiment;
        InvocationSettings invocationSettings = createInvocationSettings(testId, buildSpec, gradleExperiment);
        GradleScenarioDefinition scenarioDefinition = createScenarioDefinition(gradleExperiment, invocationSettings, invocation);
        GradleScenarioInvoker invoker = createScenarioInvoker(invocationSettings.getGradleUserHome());
        return new ScenarioBundle(invocationSettings, scenarioDefinition, invoker);
    }

    private static class ScenarioBundle {
        final InvocationSettings invocationSettings;
        final GradleScenarioDefinition scenarioDefinition;
        final GradleScenarioInvoker invoker;

        ScenarioBundle(InvocationSettings invocationSettings, GradleScenarioDefinition scenarioDefinition, GradleScenarioInvoker invoker) {
            this.invocationSettings = invocationSettings;
            this.scenarioDefinition = scenarioDefinition;
            this.invoker = invoker;
        }
    }

    private void initializeNativeServicesForTapiClient(GradleInvocationSpec buildSpec, GradleScenarioDefinition scenarioDefinition) {
        GradleConnector connector = GradleConnector.newConnector();
        try {
            connector.forProjectDirectory(buildSpec.getProjectCheckoutDirectory());
            connector.useInstallation(scenarioDefinition.getBuildConfiguration().getGradleHome());
            // First initialize the Gradle instance using the default user home dir
            // This sets some static state that uses files from the user home dir, such as DLLs
            connector.useGradleUserHomeDir(context.getGradleUserHomeDir());
            try (ProjectConnection connection = connector.connect()) {
                connection.getModel(BuildEnvironment.class);
            }
        } finally {
            connector.disconnect();
        }
    }

    private GradleScenarioInvoker createScenarioInvoker(File gradleUserHome) {
        DaemonControl daemonControl = new DaemonControl(gradleUserHome);
        return new GradleScenarioInvoker(daemonControl, pidInstrumentation);
    }

    private InvocationSettings createInvocationSettings(String testId, GradleInvocationSpec invocationSpec, GradleBuildExperimentSpec experiment) {
        GradleBuildInvoker invoker;
        if (invocationSpec.isUseAndroidStudio()) {
            invoker = GradleBuildInvoker.Ide;
        } else if (invocationSpec.isUseToolingApi()) {
            invoker = invocationSpec.isUseDaemon()
                ? GradleBuildInvoker.ToolingApi
                : GradleBuildInvoker.ToolingApi.withColdDaemon();
        } else {
            invoker = invocationSpec.isUseDaemon()
                ? GradleBuildInvoker.Cli
                : GradleBuildInvoker.Cli.withColdDaemon();
        }

        boolean measureGarbageCollection = experiment.isMeasureGarbageCollection()
            // Measuring GC needs build services which have been introduced in Gradle 6.1
            && experiment.getInvocation().getGradleDistribution().getVersion().getBaseVersion().compareTo(GradleVersion.version("6.1")) >= 0;
        return createInvocationSettingsBuilder(testId, experiment)
            .setInvoker(invoker)
            .setDryRun(false)
            .setVersions(ImmutableList.of(invocationSpec.getGradleDistribution().getVersion().getVersion()))
            .setTargets(invocationSpec.getTasksToRun())
            .setGradleUserHome(determineGradleUserHome(invocationSpec))
            .setMeasureConfigTime(false)
            .setBuildOperationMeasurements(experiment.getBuildOperationMeasurements())
            .setMeasureGarbageCollection(measureGarbageCollection)
            .setBuildLog(invocationSpec.getBuildLog())
            .setStudioConfiguration(new IdeConfiguration(invocationSpec.getStudioInstallDir(), invocationSpec.getStudioSandboxDir()))
            .build();
    }

    private static File determineGradleUserHome(GradleInvocationSpec invocationSpec) {
        return new File(invocationSpec.getWorkingDirectory(), "gradle-user-home");
    }

    private static GradleScenarioDefinition createScenarioDefinition(GradleBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings, GradleInvocationSpec invocationSpec) {
        GradleDistribution gradleDistribution = invocationSpec.getGradleDistribution();
        List<String> cleanTasks = invocationSpec.getCleanTasks();
        File gradlePropertiesFile = new File(invocationSettings.getProjectDir(), "gradle.properties");
        Properties gradleProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(gradlePropertiesFile.toPath())) {
            gradleProperties.load(inputStream);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        String[] jvmOptsFromGradleProperties = gradleProperties.getProperty("org.gradle.jvmargs").split(" ");
        final ImmutableList<String> actualJvmArgs = ImmutableList.<String>builder()
            .add(jvmOptsFromGradleProperties)
            .addAll(invocationSpec.getJvmArguments())
            .build();
        return new GradleScenarioDefinition(
            OutputDirSelectorUtil.fileSafeNameFor(experimentSpec.getDisplayName()),
            experimentSpec.getDisplayName(),
            (GradleBuildInvoker) invocationSettings.getInvoker(),
            new GradleBuildConfiguration(gradleDistribution.getVersion(), gradleDistribution.getGradleHomeDir(), Jvm.current().getJavaHome(), actualJvmArgs, false, false, invocationSpec.getClientJvmArguments()),
            experimentSpec.getInvocation().getBuildAction(),
            cleanTasks.isEmpty()
                ? BuildAction.NO_OP
                : new RunTasksAction(cleanTasks),
            invocationSpec.getArgs(),
            invocationSettings.getSystemProperties(),
            collectMutators(invocationSettings, experimentSpec),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir(),
            actualJvmArgs,
            invocationSettings.getBuildOperationMeasurements(),
            invocationSettings.isBuildOperationsTrace()
        );
    }

    private static List<BuildMutator> collectMutators(InvocationSettings invocationSettings, GradleBuildExperimentSpec experimentSpec) {
        return Stream.concat(
            experimentSpec.getBuildMutators().stream()
                .map(mutatorFunction -> mutatorFunction.apply(invocationSettings)),
            Stream.of(new DelayBeforeBuildMutator(500, MILLISECONDS))
        ).collect(Collectors.toList());
    }

    /**
     * Pause between builds for the following reasons:
     *
     * <ul>
     * <li>better simulate a normal development lifecycle where builds are rarely executed
     * back-to-back, as there is typically some work being done between the builds,</li>
     * <li>give file system events a chance to arrive; otherwise we might detect them during
     * the build, and invalidating the VFS,
     * <li>let VFS cleanup (that happens between builds) finish.</li>
     * </ul>
     */
    private static class DelayBeforeBuildMutator implements BuildMutator {
        private final long delay;
        private final TimeUnit unit;

        public DelayBeforeBuildMutator(long delay, TimeUnit unit) {
            this.unit = unit;
            this.delay = delay;
        }

        @Override
        public void beforeBuild(BuildContext context) {
            try {
                unit.sleep(delay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return String.format("Delay %d %s before each build", delay, unit.name().toLowerCase(Locale.ROOT));
        }
    }
}
