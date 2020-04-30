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

package org.gradle.testkit.scenario.collection;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.scenario.GradleScenario;
import org.gradle.testkit.scenario.GradleScenarioSteps;
import org.gradle.testkit.scenario.collection.internal.DefaultIncrementalBuildScenario;

import java.io.File;
import java.util.function.Supplier;


/**
 * Reusable {@link GradleScenario} to test incremental builds.
 *
 * Create an instance using {@link IncrementalBuildScenario#create()}.
 *
 * Also see {@link IncrementalBuildScenario.Steps}.
 *
 * @since 6.5
 */
@Incubating
public interface IncrementalBuildScenario extends GradleScenario {

    /**
     * Incremental build scenario steps.
     *
     * @since 6.5
     */
    @Incubating
    interface Steps {
        String CLEAN_BUILD = "clean-build";
        String UP_TO_DATE_BUILD = "up-to-date-build";
        String INCREMENTAL_BUILD = "incremental-build";
    }

    static IncrementalBuildScenario create() {
        return new DefaultIncrementalBuildScenario();
    }

    IncrementalBuildScenario withTaskPaths(String... taskPaths);

    IncrementalBuildScenario withInputChangeRunnerAction(Action<GradleRunner> runnerAction);

    IncrementalBuildScenario withInputChangeWorkspaceAction(Action<File> workspaceAction);

    @Override
    IncrementalBuildScenario withBaseDirectory(File baseDirectory);

    @Override
    IncrementalBuildScenario withWorkspace(Action<File> workspaceBuilder);

    @Override
    IncrementalBuildScenario withRunnerFactory(Supplier<GradleRunner> runnerFactory);

    @Override
    IncrementalBuildScenario withRunnerAction(Action<GradleRunner> runnerAction);

    @Override
    IncrementalBuildScenario withSteps(Action<GradleScenarioSteps> steps);
}
