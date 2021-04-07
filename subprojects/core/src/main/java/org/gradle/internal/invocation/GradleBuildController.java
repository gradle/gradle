/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.invocation;

import org.gradle.api.internal.GradleInternal;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class GradleBuildController implements BuildController, Stoppable {
    private enum State {Created, Completed}

    private State state = State.Created;
    private final GradleLauncher gradleLauncher;
    private final WorkerLeaseService workerLeaseService;
    private final IncludedBuildControllers includedBuildControllers;
    private final ExceptionAnalyser exceptionAnalyser;

    public GradleBuildController(GradleLauncher gradleLauncher, WorkerLeaseService workerLeaseService, IncludedBuildControllers includedBuildControllers, ExceptionAnalyser exceptionAnalyser) {
        this.gradleLauncher = gradleLauncher;
        this.workerLeaseService = workerLeaseService;
        this.includedBuildControllers = includedBuildControllers;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    public GradleBuildController(GradleLauncher gradleLauncher, IncludedBuildControllers includedBuildControllers) {
        this(gradleLauncher,
            gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class),
            includedBuildControllers,
            gradleLauncher.getGradle().getServices().get(ExceptionAnalyser.class));
    }

    public GradleBuildController(GradleLauncher gradleLauncher) {
        this(gradleLauncher,
            gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class),
            gradleLauncher.getGradle().getServices().get(IncludedBuildControllers.class),
            gradleLauncher.getGradle().getServices().get(ExceptionAnalyser.class));
    }

    public GradleLauncher getLauncher() {
        if (state == State.Completed) {
            throw new IllegalStateException("Cannot use launcher after build has completed.");
        }
        return gradleLauncher;
    }

    @Override
    public GradleInternal getGradle() {
        return getLauncher().getGradle();
    }

    @Override
    public void run() {
        doBuild(launcher -> {
            launcher.scheduleRequestedTasks();
            includedBuildControllers.startTaskExecution();
            List<Throwable> failures = new ArrayList<>();
            try {
                launcher.executeTasks();
            } catch (Exception e) {
                failures.add(e);
            }
            includedBuildControllers.awaitTaskCompletion(failures);
            RuntimeException reportableFailure = exceptionAnalyser.transform(failures);
            if (reportableFailure != null) {
                throw reportableFailure;
            }
            return null;
        });
    }

    @Override
    public void configure() {
        doBuild(GradleLauncher::getConfiguredBuild);
    }

    public <T> T doBuild(final Function<? super GradleLauncher, T> build) {
        try {
            // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
            return workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), () -> {
                List<Throwable> failures = new ArrayList<>();

                GradleLauncher launcher = getLauncher();
                T result = null;
                try {
                    result = build.apply(launcher);
                } catch (Throwable t) {
                    failures.add(t);
                }

                Consumer<Throwable> collector = failures::add;
                includedBuildControllers.finishBuild(collector);
                RuntimeException reportableFailure = exceptionAnalyser.transform(failures);
                launcher.finishBuild(reportableFailure, collector);

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

    @Override
    public void stop() {
        gradleLauncher.stop();
    }
}
