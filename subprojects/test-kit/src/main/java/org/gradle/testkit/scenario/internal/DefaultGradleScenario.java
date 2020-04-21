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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.scenario.GradleScenario;
import org.gradle.testkit.scenario.GradleScenarioSteps;
import org.gradle.testkit.scenario.ScenarioResult;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class DefaultGradleScenario extends GradleScenario {

    private File baseDirectory = new File(System.getProperty("java.io.tmpdir"));

    private Consumer<File> workspace = root -> {
    };

    private Supplier<GradleRunner> runnerFactory = GradleRunner::create;

    private Consumer<GradleScenarioSteps> steps = steps -> {
    };

    @Override
    public GradleScenario withBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
        return this;
    }

    @Override
    public GradleScenario withWorkspace(Consumer<File> workspaceBuilder) {
        this.workspace = workspaceBuilder;
        return this;
    }

    @Override
    public GradleScenario withRunnerFactory(Supplier<GradleRunner> runnerFactory) {
        this.runnerFactory = runnerFactory;
        return this;
    }

    @Override
    public GradleScenario withSteps(Consumer<GradleScenarioSteps> steps) {
        this.steps = steps;
        return this;
    }

    @Override
    public ScenarioResult run() {
        workspace.accept(baseDirectory);
        List<DefaultGradleScenarioStep> steps = buildSteps();
        LinkedHashMap<String, BuildResult> results = new LinkedHashMap<>();
        for (DefaultGradleScenarioStep step : steps) {
            if (step.getWorkspaceMutator() != null) {
                step.getWorkspaceMutator().accept(baseDirectory);
            }
            GradleRunner runner = this.runnerFactory.get();
            runner.withProjectDir(baseDirectory);
            step.getRunner().accept(runner);
            BuildResult result;
            if (step.getFailureConsumer() != null) {
                result = runner.buildAndFail();
                step.getFailureConsumer().accept(result);
            } else {
                result = runner.build();
                if (step.getResultConsumer() != null) {
                    step.getResultConsumer().accept(result);
                }
            }
            results.put(step.getName(), result);
        }
        return new DefaultScenarioResult(results);
    }

    private List<DefaultGradleScenarioStep> buildSteps() {
        DefaultGradleScenarioSteps builder = new DefaultGradleScenarioSteps();
        steps.accept(builder);
        return builder.getSteps();
    }
}
