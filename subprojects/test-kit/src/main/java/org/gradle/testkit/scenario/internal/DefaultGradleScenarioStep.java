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

package org.gradle.testkit.scenario.internal;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.scenario.GradleScenarioStep;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class DefaultGradleScenarioStep implements GradleScenarioStep {

    private final String name;
    private final List<Action<GradleRunner>> runnerCustomizations = new ArrayList<>();
    private final List<Action<File>> workspaceMutations = new ArrayList<>();
    private final List<Action<BuildResult>> resultActions = new ArrayList<>();
    private final List<Action<BuildResult>> failureActions = new ArrayList<>();

    public DefaultGradleScenarioStep(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GradleScenarioStep withRunnerCustomization(Action<GradleRunner> runnerCustomization) {
        this.runnerCustomizations.add(runnerCustomization);
        return this;
    }

    @Override
    public GradleScenarioStep withTasks(String... tasks) {
        return withRunnerCustomization(runner -> {
            List<String> args = new ArrayList<>(runner.getArguments());
            args.addAll(Arrays.asList(tasks));
            runner.withArguments(args);
        });
    }

    @Override
    public GradleScenarioStep withWorkspaceMutation(Action<File> workspaceMutation) {
        this.workspaceMutations.add(workspaceMutation);
        return this;
    }

    @Override
    public GradleScenarioStep withResult(Action<BuildResult> resultConsumer) {
        if (!failureActions.isEmpty()) {
            throw new IllegalStateException("This scenario step expect a build failure, can't also expect a success");
        }
        this.resultActions.add(resultConsumer);
        return this;
    }

    @Override
    public GradleScenarioStep withFailure(Action<BuildResult> failureConsumer) {
        if (!resultActions.isEmpty()) {
            throw new IllegalStateException("This scenario step expect a build success, can't also expect a failure");
        }
        this.failureActions.add(failureConsumer);
        return this;
    }

    @Override
    public GradleScenarioStep withFailure() {
        return withFailure(Actions.doNothing());
    }

    void customizeRunner(GradleRunner runner) {
        for (Action<GradleRunner> customization : runnerCustomizations) {
            customization.execute(runner);
        }
    }

    void mutateWorkspace(File root) {
        for (Action<File> mutation : workspaceMutations) {
            mutation.execute(root);
        }
    }

    boolean expectsFailure() {
        return !failureActions.isEmpty();
    }

    void consumeResult(BuildResult result) {
        for (Action<BuildResult> resultAction : resultActions) {
            resultAction.execute(result);
        }
    }

    void consumeFailure(BuildResult result) {
        for (Action<BuildResult> failureAction : failureActions) {
            failureAction.execute(result);
        }
    }
}
