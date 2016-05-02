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
import org.gradle.internal.Cast;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
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

    private void runMavenExperiment(MeasuredOperationList results, MavenBuildExperimentSpec experiment, final MavenInvocationSpec buildSpec) {
        File projectDir = buildSpec.getWorkingDirectory();
        performMeasurements(new InvocationExecutorProvider<InvocationCustomizer<MavenInvocationSpec>>() {
            public Runnable runner(final InvocationCustomizer<MavenInvocationSpec> invocationCustomizer) {
                return new Runnable() {
                    public void run() {
                        ExecAction mavenInvocation = createMavenInvocation(invocationCustomizer.customize(buildSpec));
                        System.out.println("Run Maven using JVM opts: " + buildSpec.getJvmOpts());
                        mavenInvocation.execute();
                    }
                };
            }
        }, experiment, results, projectDir);
    }

    @Override
    protected <S extends InvocationSpec, T extends InvocationCustomizer<S>> T createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
        if (info.getBuildExperimentSpec() instanceof MavenBuildExperimentSpec) {
            return Cast.uncheckedCast(new InvocationCustomizer<MavenInvocationSpec>() {
                public MavenInvocationSpec customize(MavenInvocationSpec invocationSpec) {
                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
                    return invocationSpec.withBuilder().args(iterationInfoArguments).build();
                }
            });
        }
        return Cast.uncheckedCast(super.<S, T>createInvocationCustomizer(info));
    }

    private ExecAction createMavenInvocation(MavenInvocationSpec buildSpec) {
        ExecAction execAction = execActionFactory.newExecAction();
        execAction.setWorkingDir(buildSpec.getWorkingDirectory());
        execAction.executable(buildSpec.getInstallation().getMvn());
        execAction.args(buildSpec.getArgs());
        execAction.args(buildSpec.getTasksToRun());
        execAction.environment("MAVEN_OPTS", Joiner.on(' ').join(Iterables.concat(buildSpec.getMavenOpts(), buildSpec.getJvmOpts())));
        return execAction;
    }


}
