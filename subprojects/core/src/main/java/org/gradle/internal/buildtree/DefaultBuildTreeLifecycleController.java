/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.buildtree;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.build.BuildLifecycleController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultBuildTreeLifecycleController implements BuildTreeLifecycleController {
    private boolean completed;
    private final BuildLifecycleController buildLifecycleController;
    private final BuildTreeWorkPreparer workPreparer;
    private final BuildTreeWorkExecutor workExecutor;
    private final BuildTreeModelCreator modelCreator;
    private final BuildTreeFinishExecutor finishExecutor;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultBuildTreeLifecycleController(BuildLifecycleController buildLifecycleController,
                                               BuildTreeWorkPreparer workPreparer,
                                               BuildTreeWorkExecutor workExecutor,
                                               BuildTreeModelCreator modelCreator,
                                               BuildTreeFinishExecutor finishExecutor,
                                               ExceptionAnalyser exceptionAnalyser) {
        this.buildLifecycleController = buildLifecycleController;
        this.workPreparer = workPreparer;
        this.modelCreator = modelCreator;
        this.workExecutor = workExecutor;
        this.finishExecutor = finishExecutor;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    @Override
    public GradleInternal getGradle() {
        if (completed) {
            throw new IllegalStateException("Cannot use Gradle object after build has finished.");
        }
        return buildLifecycleController.getGradle();
    }

    @Override
    public void scheduleAndRunTasks() {
        doBuild(failures -> {
            workPreparer.scheduleRequestedTasks();
            workExecutor.execute(failures);
            return null;
        });
    }

    @Override
    public <T> T fromBuildModel(boolean runTasks, Function<? super GradleInternal, T> action) {
        return doBuild(failureCollector -> {
            if (runTasks) {
                workPreparer.scheduleRequestedTasks();
                List<Throwable> failures = new ArrayList<>();
                workExecutor.execute(throwable -> {
                    failures.add(throwable);
                    failureCollector.accept(throwable);
                });
                if (!failures.isEmpty()) {
                    return null;
                }
            }
            return modelCreator.fromBuildModel(action);
        });
    }

    @Override
    public <T> T withEmptyBuild(Function<? super SettingsInternal, T> action) {
        return doBuild(failures -> action.apply(buildLifecycleController.getLoadedSettings()));
    }

    private <T> T doBuild(final BuildAction<T> build) {
        if (completed) {
            throw new IllegalStateException("Cannot run more than one action for this build.");
        }
        completed = true;
        List<Throwable> failures = new ArrayList<>();
        Consumer<Throwable> collector = failures::add;

        T result;
        try {
            result = build.run(collector);
        } catch (Throwable t) {
            result = null;
            failures.add(t);
        }

        finishExecutor.finishBuildTree(Collections.unmodifiableList(failures), collector);

        RuntimeException finalReportableFailure = exceptionAnalyser.transform(failures);
        if (finalReportableFailure != null) {
            throw finalReportableFailure;
        }

        return result;
    }

    private interface BuildAction<T> {
        T run(Consumer<Throwable> failures);
    }
}
