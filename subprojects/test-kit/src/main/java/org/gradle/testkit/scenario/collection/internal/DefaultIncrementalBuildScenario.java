/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario.collection.internal;

import org.gradle.api.Action;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.scenario.GradleScenarioStep;
import org.gradle.testkit.scenario.GradleScenarioSteps;
import org.gradle.testkit.scenario.ScenarioResult;
import org.gradle.testkit.scenario.collection.IncrementalBuildScenario;
import org.gradle.testkit.scenario.internal.DefaultGradleScenario;

import java.io.File;
import java.util.function.Supplier;

import static org.gradle.testkit.scenario.collection.internal.GradleScenarioUtilInternal.assertTaskOutcomes;


public class DefaultIncrementalBuildScenario extends DefaultGradleScenario implements IncrementalBuildScenario {

    private String[] taskPaths;
    private Action<GradleRunner> inputChangeRunnerAction;
    private Action<File> inputChangeWorkspaceAction;

    @Override
    public IncrementalBuildScenario withTaskPaths(String... taskPaths) {
        this.taskPaths = taskPaths;
        return this;
    }

    @Override
    public IncrementalBuildScenario withInputChangeRunnerAction(Action<GradleRunner> runnerAction) {
        this.inputChangeRunnerAction = runnerAction;
        return this;
    }

    @Override
    public IncrementalBuildScenario withInputChangeWorkspaceAction(Action<File> workspaceAction) {
        this.inputChangeWorkspaceAction = workspaceAction;
        return this;
    }

    @Override
    public ScenarioResult run() {

        withSteps(steps -> {

            steps.named(Steps.CLEAN_BUILD)
                .withArguments(taskPaths)
                .withResult(result -> assertTaskOutcomes(Steps.CLEAN_BUILD, taskPaths, result, TaskOutcome.SUCCESS));

            steps.named(Steps.UP_TO_DATE_BUILD)
                .withArguments(taskPaths)
                .withResult(result -> assertTaskOutcomes(Steps.UP_TO_DATE_BUILD, taskPaths, result, TaskOutcome.UP_TO_DATE));

            GradleScenarioStep inputChange = steps.named(Steps.INCREMENTAL_BUILD)
                .withArguments(taskPaths)
                .withResult(result -> assertTaskOutcomes(Steps.INCREMENTAL_BUILD, taskPaths, result, TaskOutcome.SUCCESS));
            if (inputChangeRunnerAction != null) {
                inputChange.withRunnerAction(inputChangeRunnerAction);
            }
            if (inputChangeWorkspaceAction != null) {
                inputChange.withWorkspaceAction(inputChangeWorkspaceAction);
            }
        });

        return super.run();
    }

    @Override
    public IncrementalBuildScenario withBaseDirectory(File baseDirectory) {
        super.withBaseDirectory(baseDirectory);
        return this;
    }

    @Override
    public IncrementalBuildScenario withWorkspace(Action<File> workspaceBuilder) {
        super.withWorkspace(workspaceBuilder);
        return this;
    }

    @Override
    public IncrementalBuildScenario withRunnerFactory(Supplier<GradleRunner> runnerFactory) {
        super.withRunnerFactory(runnerFactory);
        return this;
    }

    @Override
    public IncrementalBuildScenario withRunnerAction(Action<GradleRunner> runnerAction) {
        super.withRunnerAction(runnerAction);
        return this;
    }

    @Override
    public IncrementalBuildScenario withSteps(Action<GradleScenarioSteps> steps) {
        super.withSteps(steps);
        return this;
    }
}
