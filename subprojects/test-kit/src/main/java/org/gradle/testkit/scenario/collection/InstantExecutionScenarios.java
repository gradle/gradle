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
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.scenario.GradleScenario;
import org.gradle.testkit.scenario.GradleScenarioSteps;
import org.gradle.testkit.scenario.collection.internal.CacheInvalidationInstantExecutionScenario;
import org.gradle.testkit.scenario.collection.internal.DefaultIncrementalBuildScenario;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


/**
 * Reusable {@link GradleScenario}s to test instant execution.
 *
 * Create instances using
 * <ul>
 *     <li>{@link InstantExecutionScenarios#createIncrementalBuild()},</li>
 *     <li>{@link InstantExecutionScenarios#createBuildCache()},</li>
 *     <li>{@link InstantExecutionScenarios#createCacheInvalidation()}.</li>
 * </ul>
 *
 * @since 6.5
 */
@Incubating
public interface InstantExecutionScenarios {

    static IncrementalBuildScenario createIncrementalBuild() {
        return new DefaultIncrementalBuildScenario()
            .withRunnerAction(getEnableInstantExecution())
            .withSteps(steps -> {

                steps.named(IncrementalBuildScenario.Steps.CLEAN_BUILD)
                    .withResult(result -> getAssertStepStored().accept(IncrementalBuildScenario.Steps.CLEAN_BUILD, result));

                steps.named(IncrementalBuildScenario.Steps.UP_TO_DATE_BUILD)
                    .withResult(result -> getAssertStepLoaded().accept(IncrementalBuildScenario.Steps.UP_TO_DATE_BUILD, result));

                steps.named(IncrementalBuildScenario.Steps.INCREMENTAL_BUILD)
                    .withResult(result -> getAssertStepLoaded().accept(IncrementalBuildScenario.Steps.INCREMENTAL_BUILD, result));
            });
    }

    static BuildCacheScenario createBuildCache() {
        throw new UnsupportedOperationException("Instant execution doesn't support the build cache yet.");
    }

    static CacheInvalidation createCacheInvalidation() {
        return new CacheInvalidationInstantExecutionScenario();
    }

    /**
     * Reusable {@link GradleScenario} to test instant execution cache invalidation.
     *
     * Create an instance using {@link InstantExecutionScenarios#createCacheInvalidation()}.
     *
     * Also see {@link CacheInvalidation.Steps}.
     *
     * @since 6.5
     */
    interface CacheInvalidation extends GradleScenario {

        /**
         * Instant execution cache invalidation scenario steps.
         *
         * @since 6.5
         */
        interface Steps {
            String STORE = "store";
            String LOAD = "load";
            String INVALIDATE = "invalidate";
        }

        CacheInvalidation withTaskPaths(String... taskPaths);

        CacheInvalidation withSystemPropertyBuildLogicInput(String name, @Nullable String value1, @Nullable String value2);

        CacheInvalidation withEnvironmentVariableBuildLogicInput(String name, @Nullable String value1, @Nullable String value2);

        CacheInvalidation withFileContentBuildLogicInput(String name, @Nullable String value1, @Nullable String value2);

        @Override
        CacheInvalidation withBaseDirectory(File baseDirectory);

        @Override
        CacheInvalidation withWorkspace(Action<File> workspaceBuilder);

        @Override
        CacheInvalidation withRunnerFactory(Supplier<GradleRunner> runnerFactory);

        @Override
        CacheInvalidation withRunnerAction(Action<GradleRunner> runnerAction);

        @Override
        CacheInvalidation withSteps(Action<GradleScenarioSteps> steps);
    }

    static Action<GradleRunner> getEnableInstantExecution() {
        return runner -> {
            List<String> args = new ArrayList<>(runner.getArguments());
            args.add("-Dorg.gradle.unsafe.instant-execution=true");
            runner.withArguments(args);
        };
    }

    static BiConsumer<String, BuildResult> getAssertStepStored() {
        return (step, result) -> {
            assert result.getOutput().contains("Calculating task graph")
                : "Step '" + step + "': expected storing instant execution state";
        };
    }

    static BiConsumer<String, BuildResult> getAssertStepLoaded() {
        return (step, result) -> {
            assert result.getOutput().contains("Reusing instant execution cache")
                : "Step '" + step + "': expected loading instant execution state";
        };
    }
}
