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
import org.gradle.testkit.scenario.collection.internal.DefaultBuildCacheScenario;

import java.io.File;
import java.util.function.Supplier;

/**
 * Reusable {@link GradleScenario} to test build cacheability.
 *
 * Create an instance using {@link BuildCacheScenario#create()}.
 *
 * Also see {@link BuildCacheScenario.Steps}.
 *
 * @since 6.5
 */
@Incubating
public interface BuildCacheScenario extends GradleScenario {

    interface Steps {
        String CLEAN_BUILD = "clean-build";
        String FROM_CACHE_BUILD = "from-cache-build";
        String FROM_CACHE_RELOCATED_BUILD = "from-cache-relocated-build";
    }

    /**
     *
     */
    static BuildCacheScenario create() {
        return new DefaultBuildCacheScenario();
    }

    BuildCacheScenario withTaskPaths(String... taskPaths);

    BuildCacheScenario withoutRelocatabilityTest();

    @Override
    BuildCacheScenario withBaseDirectory(File baseDirectory);

    @Override
    BuildCacheScenario withWorkspace(Action<File> workspaceBuilder);

    @Override
    BuildCacheScenario withRunnerFactory(Supplier<GradleRunner> runnerFactory);

    @Override
    BuildCacheScenario withRunnerAction(Action<GradleRunner> runnerAction);

    @Override
    BuildCacheScenario withSteps(Action<GradleScenarioSteps> steps);
}
