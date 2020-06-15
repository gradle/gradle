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

import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.Factory;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collections;

public class GradleBuildController implements BuildController {
    private enum State {Created, Completed}

    private State state = State.Created;
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
    public GradleInternal getGradle() {
        return getLauncher().getGradle();
    }

    @Override
    public GradleInternal run() {
        return doBuild(GradleLauncher::executeTasks);
    }

    @Override
    public GradleInternal configure() {
        return doBuild(GradleLauncher::getConfiguredBuild);
    }

    private GradleInternal doBuild(final Action<? super GradleLauncher> build) {
        try {
            // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
            return workerLeaseService.withLocks(Collections.singleton(workerLeaseService.getWorkerLease()), new Factory<GradleInternal>() {
                @Override
                public GradleInternal create() {
                    GradleInternal gradle = getGradle();
                    GradleLauncher launcher = getLauncher();
                    build.execute(launcher);
                    launcher.finishBuild();
                    return gradle;
                }
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
