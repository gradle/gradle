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
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.ScenarioDefinition;
import org.gradle.profiler.ScenarioInvoker;
import org.gradle.profiler.gradle.DaemonControl;
import org.gradle.profiler.gradle.GradleBuildInvoker;
import org.gradle.profiler.gradle.GradleScenarioDefinition;
import org.gradle.profiler.gradle.GradleScenarioInvoker;
import org.gradle.profiler.gradle.RunTasksAction;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.result.BuildInvocationResult;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioDefinition;
import org.gradle.profiler.studio.invoker.StudioGradleScenarioInvoker;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * {@inheritDoc}
 *
 * This runner uses Gradle profiler to execute the actual experiment.
 */
@CompileStatic
public class GradleBuildExperimentRunner extends AbstractBuildExperimentRunner {
    private static final String GRADLE_USER_HOME_NAME = "gradleUserHome";
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

        if (!(invocationSpec instanceof GradleInvocationSpec)) {
            throw new IllegalArgumentException("Can only run Gradle invocations with Gradle profiler");
        }

        GradleInvocationSpec invocation = (GradleInvocationSpec) invocationSpec;
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
                StudioGradleScenarioDefinition studioScenarioDefinition = new StudioGradleScenarioDefinition(scenarioDefinition, buildSpec.getStudioJvmArgs());
                StudioGradleScenarioInvoker studioScenarioInvoker = new StudioGradleScenarioInvoker(scenarioInvoker);
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

    private void initializeNativeServicesForTapiClient(GradleInvocationSpec buildSpec, GradleScenarioDefinition scenarioDefinition) {
        GradleConnector connector = GradleConnector.newConnector();
        try {
            connector.forProjectDirectory(buildSpec.getWorkingDirectory());
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
            invoker = GradleBuildInvoker.AndroidStudio;
        } else if (invocationSpec.isUseToolingApi()) {
            invoker = invocationSpec.isUseDaemon()
                ? GradleBuildInvoker.ToolingApi
                : GradleBuildInvoker.ToolingApi.withColdDaemon();
        } else {
            invoker = invocationSpec.isUseDaemon()
                ? GradleBuildInvoker.Cli
                : GradleBuildInvoker.CliNoDaemon;
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
            .setMeasuredBuildOperations(experiment.getMeasuredBuildOperations())
            .setMeasureGarbageCollection(measureGarbageCollection)
            .setBuildLog(invocationSpec.getBuildLog())
            .setStudioInstallDir(invocationSpec.getStudioInstallDir())
            .setStudioSandboxDir(invocationSpec.getStudioSandboxDir())
            .build();
    }

    private File determineGradleUserHome(GradleInvocationSpec invocationSpec) {
        File projectDirectory = invocationSpec.getWorkingDirectory();
        // do not add the Gradle user home in the project directory, so it is not watched
        return new File(projectDirectory.getParent(), projectDirectory.getName() + "-" + GRADLE_USER_HOME_NAME);
    }

    private GradleScenarioDefinition createScenarioDefinition(GradleBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings, GradleInvocationSpec invocationSpec) {
        GradleDistribution gradleDistribution = invocationSpec.getGradleDistribution();
        List<String> cleanTasks = invocationSpec.getCleanTasks();
        File gradlePropertiesFile = new File(invocationSettings.getProjectDir(), "gradle.properties");
        Properties gradleProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(gradlePropertiesFile.toPath())) {
            gradleProperties.load(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            new GradleBuildConfiguration(gradleDistribution.getVersion(), gradleDistribution.getGradleHomeDir(), Jvm.current().getJavaHome(), actualJvmArgs, false, invocationSpec.getClientJvmArguments()),
            experimentSpec.getInvocation().getBuildAction(),
            cleanTasks.isEmpty()
                ? BuildAction.NO_OP
                : new RunTasksAction(cleanTasks),
            invocationSpec.getArgs(),
            invocationSettings.getSystemProperties(),
            experimentSpec.getBuildMutators().stream()
                .map(mutatorFunction -> mutatorFunction.apply(invocationSettings))
                .collect(Collectors.toList()),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir(),
            ImmutableList.of(),
            invocationSettings.getMeasuredBuildOperations()
        );
    }
}
