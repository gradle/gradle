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
import org.gradle.profiler.BuildAction;
import org.gradle.profiler.DaemonControl;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleBuildInvocationResult;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.GradleScenarioInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.RunTasksAction;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.profiler.report.CsvGenerator;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.ConnectorServices;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

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

    public GradleBuildExperimentRunner(GradleProfilerReporter gradleProfilerReporter) {
        super(gradleProfilerReporter);
        try {
            this.pidInstrumentation = new PidInstrumentation();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void doRun(BuildExperimentSpec experiment, MeasuredOperationList results) {
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
        InvocationSettings invocationSettings = createInvocationSettings(buildSpec, gradleExperiment);
        GradleScenarioDefinition scenarioDefinition = createScenarioDefinition(gradleExperiment, invocationSettings, invocation);

        try {
            GradleScenarioInvoker scenarioInvoker = createScenarioInvoker(new File(buildSpec.getWorkingDirectory(), GRADLE_USER_HOME_NAME));
            Consumer<GradleBuildInvocationResult> scenarioReporter = getResultCollector(scenarioDefinition.getName()).scenario(
                scenarioDefinition,
                scenarioInvoker.samplesFor(invocationSettings, scenarioDefinition)
            );
            AtomicInteger iterationCount = new AtomicInteger(0);
            Logging.setupLogging(workingDirectory);
            if (gradleExperiment.getInvocation().isUseToolingApi()) {
                GradleConnector connector = GradleConnector.newConnector();
                connector.forProjectDirectory(buildSpec.getWorkingDirectory());
                connector.useInstallation(scenarioDefinition.getBuildConfiguration().getGradleHome());
                // First initialize the Gradle instance using the default user home dir
                // This sets some static state that uses files from the user home dir, such as DLLs
                connector.useGradleUserHomeDir(context.getGradleUserHomeDir());
                try (ProjectConnection connection = connector.connect()) {
                    connection.getModel(BuildEnvironment.class);
                }
            }
            scenarioInvoker.doRun(scenarioDefinition,
                invocationSettings,
                consumerFor(scenarioDefinition, iterationCount, results, scenarioReporter));
            getFlameGraphGenerator().generateDifferentialGraphs(experiment);
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

    private GradleScenarioInvoker createScenarioInvoker(File gradleUserHome) {
        DaemonControl daemonControl = new DaemonControl(gradleUserHome);
        return new GradleScenarioInvoker(daemonControl, pidInstrumentation);
    }

    private InvocationSettings createInvocationSettings(GradleInvocationSpec invocationSpec, GradleBuildExperimentSpec experiment) {
        File outputDir = getFlameGraphGenerator().getJfrOutputDirectory(experiment);
        GradleBuildInvoker daemonInvoker = invocationSpec.getUseToolingApi() ? GradleBuildInvoker.ToolingApi : GradleBuildInvoker.Cli;
        GradleBuildInvoker invoker = invocationSpec.isUseDaemon()
            ? daemonInvoker
            : (daemonInvoker == GradleBuildInvoker.ToolingApi
            ? daemonInvoker.withColdDaemon()
            : GradleBuildInvoker.CliNoDaemon);
        boolean measureGarbageCollection = experiment.isMeasureGarbageCollection()
            // Measuring GC needs build services which have been introduced in Gradle 6.1
            && experiment.getInvocation().getGradleDistribution().getVersion().getBaseVersion().compareTo(GradleVersion.version("6.1")) >= 0;
        return new InvocationSettings.InvocationSettingsBuilder()
            .setProjectDir(invocationSpec.getWorkingDirectory())
            .setProfiler(getProfiler())
            .setBenchmark(true)
            .setOutputDir(outputDir)
            .setInvoker(invoker)
            .setDryRun(false)
            .setScenarioFile(null)
            .setVersions(ImmutableList.of(invocationSpec.getGradleDistribution().getVersion().getVersion()))
            .setTargets(invocationSpec.getTasksToRun())
            .setSysProperties(emptyMap())
            .setGradleUserHome(new File(invocationSpec.getWorkingDirectory(), GRADLE_USER_HOME_NAME))
            .setWarmupCount(warmupsForExperiment(experiment))
            .setIterations(invocationsForExperiment(experiment))
            .setMeasureConfigTime(false)
            .setMeasuredBuildOperations(experiment.getMeasuredBuildOperations())
            .setMeasureGarbageCollection(measureGarbageCollection)
            .setCsvFormat(CsvGenerator.Format.LONG)
            .setBuildLog(invocationSpec.getBuildLog())
            .build();
    }

    private GradleScenarioDefinition createScenarioDefinition(GradleBuildExperimentSpec experimentSpec, InvocationSettings invocationSettings, GradleInvocationSpec invocationSpec) {
        GradleDistribution gradleDistribution = invocationSpec.getGradleDistribution();
        List<String> cleanTasks = invocationSpec.getCleanTasks();
        return new GradleScenarioDefinition(
            safeScenarioName(experimentSpec.getDisplayName()),
            experimentSpec.getDisplayName(),
            (GradleBuildInvoker) invocationSettings.getInvoker(),
            new GradleBuildConfiguration(gradleDistribution.getVersion(), gradleDistribution.getGradleHomeDir(), Jvm.current().getJavaHome(), invocationSpec.getJvmOpts(), false),
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

    private String safeScenarioName(String name) {
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
