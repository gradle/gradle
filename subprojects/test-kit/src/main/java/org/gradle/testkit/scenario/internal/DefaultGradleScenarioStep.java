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
import org.gradle.testkit.scenario.GradleScenarioStep;

import javax.annotation.Nullable;
import java.io.File;
import java.util.function.Consumer;


public class DefaultGradleScenarioStep implements GradleScenarioStep {

    private final String name;

    private Consumer<GradleRunner> runner = runner -> {
    };

    private Consumer<File> workspaceMutator;

    private Consumer<BuildResult> resultConsumer;

    private Consumer<BuildResult> failureConsumer;

    public DefaultGradleScenarioStep(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public GradleScenarioStep withRunnerCustomization(Consumer<GradleRunner> runner) {
        this.runner = runner;
        return this;
    }

    @Override
    public GradleScenarioStep withWorkspaceMutation(Consumer<File> workspaceMutator) {
        this.workspaceMutator = workspaceMutator;
        return this;
    }

    @Override
    public GradleScenarioStep withResult(Consumer<BuildResult> resultConsumer) {
        if (failureConsumer != null) {
            throw new IllegalStateException("This scenario step expect a build failure, can't also expect a success");
        }
        this.resultConsumer = resultConsumer;
        return this;
    }

    @Override
    public GradleScenarioStep withFailure(Consumer<BuildResult> failureConsumer) {
        if (resultConsumer != null) {
            throw new IllegalStateException("This scenario step expect a build success, can't also expect a failure");
        }
        this.failureConsumer = failureConsumer;
        return this;
    }

    Consumer<GradleRunner> getRunner() {
        return runner;
    }

    @Nullable
    Consumer<File> getWorkspaceMutator() {
        return workspaceMutator;
    }

    @Nullable
    Consumer<BuildResult> getResultConsumer() {
        return resultConsumer;
    }

    @Nullable
    Consumer<BuildResult> getFailureConsumer() {
        return failureConsumer;
    }
}
