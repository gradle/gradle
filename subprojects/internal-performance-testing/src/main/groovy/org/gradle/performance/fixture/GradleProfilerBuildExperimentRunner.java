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
import com.google.common.collect.ImmutableMap;
import org.gradle.integtests.fixtures.executer.GradleDistribution;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.profiler.BuildAction;
import org.gradle.profiler.BuildMutatorFactory;
import org.gradle.profiler.DaemonControl;
import org.gradle.profiler.GradleBuildConfiguration;
import org.gradle.profiler.GradleBuildInvoker;
import org.gradle.profiler.GradleScenarioDefinition;
import org.gradle.profiler.GradleScenarioInvoker;
import org.gradle.profiler.InvocationSettings;
import org.gradle.profiler.Logging;
import org.gradle.profiler.Profiler;
import org.gradle.profiler.RunTasksAction;
import org.gradle.profiler.instrument.PidInstrumentation;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GradleProfilerBuildExperimentRunner implements BuildExperimentRunner {

    private final String jfrProfileTargetDir;

    public GradleProfilerBuildExperimentRunner() {
        jfrProfileTargetDir = org.gradle.performance.fixture.Profiler.getJfrProfileTargetDir();
    }

    @Override
    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();
        workingDirectory.mkdirs();
        copyTemplateTo(experiment, workingDirectory);

        if (experiment.getListener() != null) {
            experiment.getListener().beforeExperiment(experiment, workingDirectory);
        }

        if (!(invocationSpec instanceof GradleInvocationSpec)) {
            throw new IllegalArgumentException("Can only run Gradle invocations with Gradle profiler");
        }

        GradleInvocationSpec invocation = (GradleInvocationSpec) invocationSpec;
        List<String> additionalJvmOpts = new ArrayList<>();
        List<String> additionalArgs = new ArrayList<>();
        additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());

        GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);

        InvocationSettings invocationSettings = createInvocationSettings(buildSpec, experiment);
        GradleScenarioDefinition scenarioDefinition = createScenarioDefinition(experiment.getDisplayName(), invocationSettings, invocation);

        try {
            GradleScenarioInvoker scenarioInvoker = createScenarioInvoker(new File(buildSpec.getWorkingDirectory(), "gradleUserHome"));
            AtomicInteger iterationCount = new AtomicInteger(0);
            int warmUpCount = scenarioDefinition.getWarmUpCount();
            Logging.setupLogging(workingDirectory);
            scenarioInvoker.doRun(scenarioDefinition, invocationSettings, invocationResult -> {
                int currentIteration = iterationCount.incrementAndGet();
                if (currentIteration > warmUpCount) {
                    MeasuredOperation measuredOperation = new MeasuredOperation();
                    measuredOperation.setTotalTime(Duration.millis(invocationResult.getExecutionTime().toMillis()));
                    results.add(measuredOperation);
                }
            });
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

    private GradleScenarioInvoker createScenarioInvoker(File gradleUserHome) throws IOException {
        DaemonControl daemonControl = new DaemonControl(gradleUserHome);
        PidInstrumentation pidInstrumentation = new PidInstrumentation();
        return new GradleScenarioInvoker(daemonControl, pidInstrumentation);
    }

    private InvocationSettings createInvocationSettings(GradleInvocationSpec invocationSpec, BuildExperimentSpec experiment) {
        // TODO: use an output directory outside of the working directory
        File outputDir = jfrProfileTargetDir == null
            ? new File(invocationSpec.getWorkingDirectory(), "profile-out")
            : new File(jfrProfileTargetDir);
        return new InvocationSettings(
            invocationSpec.getWorkingDirectory(),
            jfrProfileTargetDir == null
                ? Profiler.NONE
                : new org.gradle.profiler.jfr.JfrProfiler(),
            true,
            outputDir,
            invocationSpec.getUseToolingApi() ? GradleBuildInvoker.ToolingApi : GradleBuildInvoker.Cli,
            false,
            null,
            ImmutableList.of(invocationSpec.getGradleDistribution().getVersion().getVersion()),
            invocationSpec.getTasksToRun(),
            ImmutableMap.of(),
            new File(invocationSpec.getWorkingDirectory(), "gradle-user-home"),
            warmupsForExperiment(experiment),
            invocationsForExperiment(experiment),
            false,
            ImmutableList.of("org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType")
        );
    }


    private GradleScenarioDefinition createScenarioDefinition(String displayName, InvocationSettings invocationSettings, GradleInvocationSpec invocationSpec) {
        GradleDistribution gradleDistribution = invocationSpec.getGradleDistribution();
        List<String> cleanTasks = invocationSpec.getCleanTasks();
        return new GradleScenarioDefinition(
            displayName,
            (GradleBuildInvoker) invocationSettings.getInvoker(),
            new GradleBuildConfiguration(gradleDistribution.getVersion(), gradleDistribution.getGradleHomeDir(), Jvm.current().getJavaHome(), invocationSpec.getJvmOpts(), false),
            new RunTasksAction(invocationSettings.getTargets()),
            cleanTasks.isEmpty()
                ? BuildAction.NO_OP
                : new RunTasksAction(cleanTasks),
            invocationSpec.getArgs(),
            invocationSettings.getSystemProperties(),
            new BuildMutatorFactory(ImmutableList.of()),
            invocationSettings.getWarmUpCount(),
            invocationSettings.getBuildCount(),
            invocationSettings.getOutputDir(),
            ImmutableList.of(),
            invocationSettings.getMeasuredBuildOperations()
        );
    }


    private void copyTemplateTo(BuildExperimentSpec experiment, File workingDir) {
        File templateDir = new TestProjectLocator().findProjectDir(experiment.getProjectName());
        GFileUtils.cleanDirectory(workingDir);
        GFileUtils.copyDirectory(templateDir, workingDir);
    }

    private static String getExperimentOverride(String key) {
        String value = System.getProperty("org.gradle.performance.execution." + key);
        if (value != null && !"defaults".equals(value)) {
            return value;
        }
        return null;
    }

    protected Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        String overriddenInvocationCount = getExperimentOverride("runs");
        if (overriddenInvocationCount != null) {
            return Integer.valueOf(overriddenInvocationCount);
        }
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        return 40;
    }

    protected int warmupsForExperiment(BuildExperimentSpec experiment) {
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

    private boolean usesDaemon(BuildExperimentSpec experiment) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon()) {
                return true;
            }
        }
        return false;
    }
}
