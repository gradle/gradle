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
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;


public class DefaultGradleScenarioStep implements GradleScenarioStep {

    private final String name;
    private final List<Action<GradleRunner>> runnerActions = new ArrayList<>();
    private boolean cleanWorkspace;
    private boolean relocatedWorkspace;
    private final List<Action<File>> workspaceActions = new ArrayList<>();
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
    public GradleScenarioStep withRunnerAction(Action<GradleRunner> runnerAction) {
        this.runnerActions.add(runnerAction);
        return this;
    }

    @Override
    public GradleScenarioStep withArguments(String... arguments) {
        return withRunnerAction(runner -> {
            List<String> args = new ArrayList<>(runner.getArguments());
            args.addAll(Arrays.asList(arguments));
            runner.withArguments(args);
        });
    }

    @Override
    public GradleScenarioStep withCleanWorkspace() {
        this.cleanWorkspace = true;
        return this;
    }

    @Override
    public GradleScenarioStep withRelocatedWorkspace() {
        this.relocatedWorkspace = true;
        return this;
    }

    @Override
    public GradleScenarioStep withWorkspaceAction(Action<File> workspaceAction) {
        this.workspaceActions.add(workspaceAction);
        return this;
    }

    @Override
    public GradleScenarioStep withResult(Action<BuildResult> resultConsumer) {
        if (!failureActions.isEmpty()) {
            throw new IllegalStateException("This scenario step expects a build failure, can't also expect a success");
        }
        this.resultActions.add(resultConsumer);
        return this;
    }

    @Override
    public GradleScenarioStep withFailure(Action<BuildResult> failureConsumer) {
        if (!resultActions.isEmpty()) {
            throw new IllegalStateException("This scenario step expects a build success, can't also expect a failure");
        }
        this.failureActions.add(failureConsumer);
        return this;
    }

    @Override
    public GradleScenarioStep withFailure() {
        return withFailure(Actions.doNothing());
    }

    void executeRunnerActions(GradleRunner runner) {
        for (Action<GradleRunner> runnerAction : runnerActions) {
            runnerAction.execute(runner);
        }
    }

    File prepareWorkspace(File currentWorkspace, Supplier<File> nextWorkspaceSupplier, Action<File> workspaceBuilder) {
        if (relocatedWorkspace) {
            File nextWorkspace = nextWorkspaceSupplier.get();
            if (cleanWorkspace) {
                GFileUtils.mkdirs(nextWorkspace);
                workspaceBuilder.execute(nextWorkspace);
            } else {
                GFileUtils.copyDirectory(currentWorkspace, nextWorkspace);
            }
            for (Action<File> workspaceAction : workspaceActions) {
                workspaceAction.execute(nextWorkspace);
            }
            return nextWorkspace;
        }
        if (cleanWorkspace) {
            GFileUtils.deleteDirectory(currentWorkspace);
            GFileUtils.mkdirs(currentWorkspace);
            workspaceBuilder.execute(currentWorkspace);
        }
        for (Action<File> workspaceAction : workspaceActions) {
            workspaceAction.execute(currentWorkspace);
        }
        return currentWorkspace;
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
