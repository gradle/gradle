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
import org.gradle.testkit.scenario.GradleScenarioStep;
import org.gradle.testkit.scenario.GradleScenarioSteps;
import org.gradle.testkit.scenario.ScenarioResult;
import org.gradle.testkit.scenario.collection.InstantExecutionScenarios;
import org.gradle.testkit.scenario.internal.DefaultGradleScenario;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;


public class CacheInvalidationInstantExecutionScenario
    extends DefaultGradleScenario
    implements InstantExecutionScenarios.CacheInvalidation {

    private static class BuildLogicInput {

        private final String name;
        private final String value1;
        private final String value2;

        BuildLogicInput(String name, @Nullable String value1, @Nullable String value2) {
            this.name = name;
            this.value1 = value1;
            this.value2 = value2;
        }

        public String getName() {
            return name;
        }

        @Nullable
        public String getValue1() {
            return value1;
        }

        @Nullable
        public String getValue2() {
            return value2;
        }

        boolean hasValue1() {
            return value1 != null;
        }

        boolean hasValue2() {
            return value2 != null;
        }
    }

    private String[] taskPaths;
    private final List<BuildLogicInput> systemProperties = new ArrayList<>();
    private final List<BuildLogicInput> envVariables = new ArrayList<>();
    private final List<BuildLogicInput> fileContents = new ArrayList<>();

    @Override
    public InstantExecutionScenarios.CacheInvalidation withTaskPaths(String... taskPaths) {
        this.taskPaths = taskPaths;
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withSystemPropertyBuildLogicInput(String name, @Nullable String value1, @Nullable String value2) {
        systemProperties.add(new BuildLogicInput(name, value1, value2));
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withEnvironmentVariableBuildLogicInput(String name, @Nullable String value1, @Nullable String value2) {
        envVariables.add(new BuildLogicInput(name, value1, value2));
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withFileContentBuildLogicInput(String name, @Nullable String value1, @Nullable String value2) {
        fileContents.add(new BuildLogicInput(name, value1, value2));
        return this;
    }

    @Override
    public ScenarioResult run() {

        withRunnerAction(InstantExecutionScenarios.getEnableInstantExecution());

        withSteps(steps -> {

            GradleScenarioStep store = steps.named(Steps.STORE)
                .withResult(result -> InstantExecutionScenarios.getAssertStepStored().accept(Steps.STORE, result));

            GradleScenarioStep load = steps.named(Steps.LOAD)
                .withResult(result -> InstantExecutionScenarios.getAssertStepLoaded().accept(Steps.LOAD, result));

            GradleScenarioStep invalidate = steps.named(Steps.INVALIDATE)
                .withResult(result -> InstantExecutionScenarios.getAssertStepStored().accept(Steps.INVALIDATE, result));

            applySystemProperties(load, store, invalidate);
            applyEnvironmentVariables(load, store, invalidate);
            applyFileContents(load, store, invalidate);

            if (taskPaths != null) {
                store.withArguments(taskPaths);
                load.withArguments(taskPaths);
                invalidate.withArguments(taskPaths);
            }
        });

        return super.run();
    }

    private void applySystemProperties(GradleScenarioStep load, GradleScenarioStep store, GradleScenarioStep invalidate) {

        String[] systemProperties1 = systemProperties.stream()
            .filter(BuildLogicInput::hasValue1)
            .map(input -> "-D" + input.name + "=" + input.value1)
            .toArray(String[]::new);

        if (systemProperties1.length > 0) {
            store.withArguments(systemProperties1);
            load.withArguments(systemProperties1);
        }

        String[] systemProperties2 = systemProperties.stream()
            .filter(BuildLogicInput::hasValue2)
            .map(input -> "-D" + input.name + "=" + input.value2)
            .toArray(String[]::new);

        if (systemProperties2.length > 0) {
            invalidate.withArguments(systemProperties2);
        }
    }

    private void applyEnvironmentVariables(GradleScenarioStep load, GradleScenarioStep store, GradleScenarioStep invalidate) {

        Map<String, String> envVariables1 = envVariables.stream()
            .filter(BuildLogicInput::hasValue1)
            .collect(toMap(BuildLogicInput::getName, BuildLogicInput::getValue1));

        if (!envVariables1.isEmpty()) {
            Action<GradleRunner> runnerAction = runner -> runner.withEnvironment(envVariables1);
            store.withRunnerAction(runnerAction);
            load.withRunnerAction(runnerAction);
        }

        Map<String, String> envVariables2 = envVariables.stream()
            .filter(BuildLogicInput::hasValue2)
            .collect(toMap(BuildLogicInput::getName, BuildLogicInput::getValue2));

        if (!envVariables2.isEmpty()) {
            invalidate.withRunnerAction(runner -> runner.withEnvironment(envVariables2));
        }
    }

    private void applyFileContents(GradleScenarioStep load, GradleScenarioStep store, GradleScenarioStep invalidate) {

        Function<Map<String, String>, Action<File>> workspaceActionFactory = fileContentByPath ->
            root ->
                fileContentByPath.forEach((path, content) -> {
                    File file = new File(root, path);
                    if (content == null) {
                        GFileUtils.deleteQuietly(file);
                    } else {
                        GFileUtils.writeFile(content, file);
                    }
                });

        Map<String, String> fileContents1 = fileContents.stream()
            .collect(toMap(BuildLogicInput::getName, BuildLogicInput::getValue1));

        if (!fileContents1.isEmpty()) {
            Action<File> workspaceAction = workspaceActionFactory.apply(fileContents1);
            load.withWorkspaceAction(workspaceAction);
            store.withWorkspaceAction(workspaceAction);
        }

        Map<String, String> fileContents2 = fileContents.stream()
            .collect(toMap(BuildLogicInput::getName, BuildLogicInput::getValue2));

        if (!fileContents2.isEmpty()) {
            invalidate.withWorkspaceAction(workspaceActionFactory.apply(fileContents2));
        }
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withBaseDirectory(File baseDirectory) {
        super.withBaseDirectory(baseDirectory);
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withWorkspace(Action<File> workspaceBuilder) {
        super.withWorkspace(workspaceBuilder);
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withRunnerFactory(Supplier<GradleRunner> runnerFactory) {
        super.withRunnerFactory(runnerFactory);
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withRunnerAction(Action<GradleRunner> runnerAction) {
        super.withRunnerAction(runnerAction);
        return this;
    }

    @Override
    public InstantExecutionScenarios.CacheInvalidation withSteps(Action<GradleScenarioSteps> steps) {
        super.withSteps(steps);
        return this;
    }
}
