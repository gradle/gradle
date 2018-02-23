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
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

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

    private void runMavenExperiment(MeasuredOperationList results, final MavenBuildExperimentSpec experiment, final MavenInvocationSpec buildSpec) {
        File projectDir = buildSpec.getWorkingDirectory();
        performMeasurements(new InvocationExecutorProvider() {
            public Action<MeasuredOperation> runner(final BuildExperimentInvocationInfo invocationInfo, final InvocationCustomizer invocationCustomizer) {
                return new Action<MeasuredOperation>() {
                    @Override
                    public void execute(MeasuredOperation measuredOperation) {
                        System.out.println("Run Maven using JVM opts: " + Iterables.concat(buildSpec.getMavenOpts(), buildSpec.getJvmOpts()));
                        List<String> cleanTasks = buildSpec.getCleanTasks();
                        if (!cleanTasks.isEmpty()) {
                            System.out.println("Cleaning up by running Maven tasks: " + Joiner.on(" ").join(buildSpec.getCleanTasks()));
                            ExecAction clean = createMavenInvocation(buildSpec, cleanTasks);
                            executeWithFileLogging(experiment, clean);
                        }

                        MavenInvocationSpec invocation = invocationCustomizer.customize(invocationInfo, buildSpec);
                        final ExecAction run = createMavenInvocation(invocation, invocation.getTasksToRun());
                        System.out.println("Measuring Maven tasks: " + Joiner.on(" ").join(buildSpec.getTasksToRun()));
                        DurationMeasurementImpl.measure(measuredOperation, new Runnable() {
                            @Override
                            public void run() {
                                executeWithFileLogging(experiment, run);
                            }
                        });
                    }
                };
            }
        }, experiment, results, projectDir);
    }

    private void executeWithFileLogging(MavenBuildExperimentSpec experiment, ExecAction mavenInvocation) {
        File log = new File(experiment.getWorkingDirectory(), "log.txt");
        OutputStream out = null;
        try {
            out = new FileOutputStream(log, true);
            mavenInvocation.setErrorOutput(out);
            mavenInvocation.setStandardOutput(out);
            mavenInvocation.execute();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            CompositeStoppable.stoppable(out).stop();
        }
    }

    @Override
    protected InvocationCustomizer createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
        if (info.getBuildExperimentSpec() instanceof MavenBuildExperimentSpec) {
            return new InvocationCustomizer() {
                public InvocationSpec customize(BuildExperimentInvocationInfo info, InvocationSpec invocationSpec) {
                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
                    return ((MavenInvocationSpec) invocationSpec).withBuilder().args(iterationInfoArguments).build();
                }
            };
        }
        return super.createInvocationCustomizer(info);
    }

    private ExecAction createMavenInvocation(MavenInvocationSpec buildSpec, List<String> tasks) {
        ExecAction execAction = execActionFactory.newExecAction();
        execAction.setWorkingDir(buildSpec.getWorkingDirectory());
        execAction.executable(buildSpec.getInstallation().getMvn());
        execAction.args(buildSpec.getArgs());
        execAction.args(tasks);
        execAction.environment("MAVEN_OPTS", Joiner.on(' ').join(Iterables.concat(buildSpec.getMavenOpts(), buildSpec.getJvmOpts())));
        return execAction;
    }


}
