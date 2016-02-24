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

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class GradleVsMavenBuildExperimentRunner extends BuildExperimentRunner {
    private final ExecActionFactory execActionFactory;

    public GradleVsMavenBuildExperimentRunner(GradleSessionProvider executerProvider, ExecActionFactory execActionFactory) {
        super(executerProvider);
        this.execActionFactory = execActionFactory;
    }

    public void run(BuildExperimentSpec exp, MeasuredOperationList results) {
        super.run(exp, results);
        InvocationSpec invocation = exp.getInvocation();
        if (invocation instanceof MavenInvocationSpec) {
            List<String> additionalJvmOpts = getDataCollector().getAdditionalJvmOpts(invocation.getWorkingDirectory());
            MavenInvocationSpec mavenInvocationSpec = (MavenInvocationSpec) invocation;
            mavenInvocationSpec = mavenInvocationSpec.withBuilder().jvmOpts(additionalJvmOpts).build();
            MavenBuildExperimentSpec experiment = (MavenBuildExperimentSpec) exp;
            runMavenExperiment(results, experiment, mavenInvocationSpec);
        }
    }

    private void runMavenExperiment(MeasuredOperationList results, MavenBuildExperimentSpec experiment, MavenInvocationSpec buildSpec) {
        File projectDir = buildSpec.getWorkingDirectory();

        int warmUpCount = warmupsForExperiment(experiment);
        for (int i = 0; i < warmUpCount; i++) {
            System.out.println();
            System.out.println(String.format("Warm-up #%s", i + 1));
            runOnce(experiment, buildSpec, results, projectDir, Phase.WARMUP, i + 1, warmUpCount);
        }
        waitForMillis(experiment, experiment.getSleepAfterWarmUpMillis());
        int invocationCount = invocationsForExperiment(experiment);
        for (int i = 0; i < invocationCount; i++) {
            if (i > 0) {
                waitForMillis(experiment, experiment.getSleepAfterTestRoundMillis());
            }
            System.out.println();
            System.out.println(String.format("Test run #%s", i + 1));
            runOnce(experiment, buildSpec, results, projectDir, Phase.MEASUREMENT, i + 1, invocationCount);
        }
    }

    private ExecAction createMavenInvocation(MavenInvocationSpec buildSpec) {
        ExecAction execAction = execActionFactory.newExecAction();
        execAction.setWorkingDir(buildSpec.getWorkingDirectory());
        execAction.executable(buildSpec.getInstallation().getMvn());
        execAction.args(buildSpec.getArgs());
        execAction.args(buildSpec.getTasksToRun());
        execAction.environment("MAVEN_OPTS", Joiner.on(' ').join(buildSpec.getJvmOpts()));
        return execAction;
    }

    private void runOnce(final MavenBuildExperimentSpec experiment, final MavenInvocationSpec buildSpec, MeasuredOperationList results, final File projectDir, final Phase phase, final int iterationNumber, final int iterationMax) {
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

        final Runnable runner = new Runnable() {
            public void run() {
                ExecAction mavenInvocation = createMavenInvocation(buildSpec);
                mavenInvocation.execute();
            }
        };

        if (experiment.getListener() != null) {
            experiment.getListener().beforeInvocation(invocationInfo);
        }

        MeasuredOperation operation = getTimer().measure(new Action<MeasuredOperation>() {
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
                getDataCollector().collect(invocationInfo, operation);
            }

            results.add(operation);
        }
    }

}
