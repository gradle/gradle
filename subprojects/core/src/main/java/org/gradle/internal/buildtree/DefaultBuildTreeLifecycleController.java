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
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultBuildTreeLifecycleController implements BuildTreeLifecycleController {
    private boolean completed;
    private final BuildLifecycleController buildLifecycleController;
    private final WorkerLeaseService workerLeaseService;
    private final BuildTreeWorkExecutor workExecutor;
    private final BuildTreeFinishExecutor finishExecutor;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultBuildTreeLifecycleController(BuildLifecycleController buildLifecycleController,
                                               WorkerLeaseService workerLeaseService,
                                               BuildTreeWorkExecutor workExecutor,
                                               BuildTreeFinishExecutor finishExecutor,
                                               ExceptionAnalyser exceptionAnalyser) {
        this.buildLifecycleController = buildLifecycleController;
        this.workerLeaseService = workerLeaseService;
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
        doBuild((buildController, failures) -> {
            buildController.scheduleRequestedTasks();
            workExecutor.execute(failures);
            return null;
        });
    }

    @Override
    public <T> T fromBuildModel(boolean runTasks, Function<? super GradleInternal, T> action) {
        return doBuild((buildController, failureCollector) -> {
            if (runTasks) {
                buildController.scheduleRequestedTasks();
                List<Throwable> failures = new ArrayList<>();
                workExecutor.execute(throwable -> {
                    failures.add(throwable);
                    failureCollector.accept(throwable);
                });
                if (!failures.isEmpty()) {
                    return null;
                }
            } else {
                buildController.getConfiguredBuild();
            }
            return action.apply(buildController.getGradle());
        });
    }

    @Override
    public <T> T withEmptyBuild(Function<? super SettingsInternal, T> action) {
        return doBuild((buildController, failures) -> action.apply(buildController.getLoadedSettings()));
    }

    private <T> T doBuild(final BuildAction<T> build) {
        if (completed) {
            throw new IllegalStateException("Cannot run more than one action for this build.");
        }
        completed = true;
        // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
        return workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), () -> {
            List<Throwable> failures = new ArrayList<>();
            Consumer<Throwable> collector = failures::add;

            T result;
            try {
                result = build.run(buildLifecycleController, collector);
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
        });
    }

    private interface BuildAction<T> {
        T run(BuildLifecycleController buildLifecycleController, Consumer<Throwable> failures);
    }
}
