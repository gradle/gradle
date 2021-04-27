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
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultBuildTreeLifecycleController implements BuildTreeLifecycleController {
    private enum State {Created, Running, Completed}

    private State state = State.Created;
    private final BuildLifecycleController buildLifecycleController;
    private final WorkerLeaseService workerLeaseService;
    private final IncludedBuildControllers includedBuildControllers;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultBuildTreeLifecycleController(BuildLifecycleController buildLifecycleController,
                                               WorkerLeaseService workerLeaseService,
                                               IncludedBuildControllers includedBuildControllers,
                                               ExceptionAnalyser exceptionAnalyser) {
        this.buildLifecycleController = buildLifecycleController;
        this.workerLeaseService = workerLeaseService;
        this.includedBuildControllers = includedBuildControllers;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    public DefaultBuildTreeLifecycleController(BuildLifecycleController buildLifecycleController, IncludedBuildControllers includedBuildControllers) {
        this(buildLifecycleController,
            buildLifecycleController.getGradle().getServices().get(WorkerLeaseService.class),
            includedBuildControllers,
            buildLifecycleController.getGradle().getServices().get(ExceptionAnalyser.class));
    }

    public DefaultBuildTreeLifecycleController(BuildLifecycleController buildLifecycleController) {
        this(buildLifecycleController,
            buildLifecycleController.getGradle().getServices().get(WorkerLeaseService.class),
            buildLifecycleController.getGradle().getServices().get(IncludedBuildControllers.class),
            buildLifecycleController.getGradle().getServices().get(ExceptionAnalyser.class));
    }

    @Override
    public GradleInternal getGradle() {
        if (state == State.Completed) {
            throw new IllegalStateException("Cannot use Gradle object after build has finished.");
        }
        return buildLifecycleController.getGradle();
    }

    @Override
    public void run() {
        doBuild((gradleLauncher, failures) -> {
            gradleLauncher.scheduleRequestedTasks();
            includedBuildControllers.startTaskExecution();
            try {
                gradleLauncher.executeTasks();
            } catch (Exception e) {
                failures.accept(e);
            }
            includedBuildControllers.awaitTaskCompletion(failures);
            return null;
        });
    }

    @Override
    public void configure() {
        doBuild((gradleLauncher, failures) -> gradleLauncher.getConfiguredBuild());
    }

    @Override
    public <T> T withEmptyBuild(Function<SettingsInternal, T> action) {
        return doBuild((gradleLauncher, failures) -> action.apply(gradleLauncher.getLoadedSettings()));
    }

    private <T> T doBuild(final BuildAction<T> build) {
        if (state != State.Created) {
            throw new IllegalStateException("Cannot run more than one action for this build.");
        }
        state = State.Running;
        try {
            // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
            return workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), () -> {
                List<Throwable> failures = new ArrayList<>();
                Consumer<Throwable> collector = failures::add;

                T result = null;
                try {
                    result = build.run(buildLifecycleController, collector);
                } catch (Throwable t) {
                    failures.add(t);
                }

                includedBuildControllers.finishBuild(collector);
                RuntimeException reportableFailure = exceptionAnalyser.transform(failures);
                buildLifecycleController.finishBuild(reportableFailure, collector);

                RuntimeException finalReportableFailure = exceptionAnalyser.transform(failures);
                if (finalReportableFailure != null) {
                    throw finalReportableFailure;
                }

                return result;
            });
        } finally {
            state = State.Completed;
        }
    }

    private interface BuildAction<T> {
        T run(BuildLifecycleController buildLifecycleController, Consumer<Throwable> failures);
    }
}
