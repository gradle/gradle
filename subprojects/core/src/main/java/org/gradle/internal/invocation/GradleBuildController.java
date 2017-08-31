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
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collections;
import java.util.concurrent.Callable;

public class GradleBuildController implements BuildController {
    private enum State {Created, Completed}

    private State state = State.Created;
    private boolean hasResult;
    private Object result;
    private final GradleLauncher gradleLauncher;
    private final WorkerLeaseService workerLeaseService;

    public GradleBuildController(GradleLauncher gradleLauncher, WorkerLeaseService workerLeaseService) {
        this.gradleLauncher = gradleLauncher;
        this.workerLeaseService = workerLeaseService;
    }

    public GradleBuildController(GradleLauncher gradleLauncher) {
        this(gradleLauncher, gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class));
    }

    public GradleLauncher getLauncher() {
        if (state == State.Completed) {
            throw new IllegalStateException("Cannot use launcher after build has completed.");
        }
        return gradleLauncher;
    }

    @Override
    public boolean hasResult() {
        return hasResult;
    }

    @Override
    public Object getResult() {
        if (!hasResult) {
            throw new IllegalStateException("No result has been provided for this build action.");
        }
        return result;
    }

    @Override
    public void setResult(Object result) {
        this.hasResult = true;
        this.result = result;
    }

    public GradleInternal getGradle() {
        return getLauncher().getGradle();
    }

    public GradleInternal run() {
        return doBuild(new Callable<GradleInternal>() {
            @Override
            public GradleInternal call() throws Exception {
                return getLauncher().executeTasks();
            }
        });
    }

    public GradleInternal configure() {
        return doBuild(new Callable<GradleInternal>() {
            @Override
            public GradleInternal call() throws Exception {
                GradleInternal gradle = getLauncher().getConfiguredBuild();
                getLauncher().finishBuild();
                return gradle;
            }
        });
    }

    private GradleInternal doBuild(final Callable<GradleInternal> build) {
        GradleInternal gradle = getGradle();
        BuildOperationExecutor buildOperationExecutor = gradle.getServices().get(BuildOperationExecutor.class);
        gradle.setBuildOperation(buildOperationExecutor.getCurrentOperation());
        try {
            // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
            return workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), build);
        } finally {
            gradle.setBuildOperation(null);
            state = State.Completed;
        }
    }

    @Override
    public void stop() {
        gradleLauncher.stop();
    }
}
