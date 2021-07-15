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

package org.gradle.composite.internal;

import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

class DefaultIncludedBuildController extends AbstractIncludedBuildController {
    private final IncludedBuildState includedBuild;
    private final ProjectStateRegistry projectStateRegistry;
    private final WorkerLeaseRegistry.WorkerLease parentLease;
    private final WorkerLeaseService workerLeaseService;

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition stateChange = lock.newCondition();
    private boolean finished;
    private final List<Throwable> executionFailures = new ArrayList<>();

    public DefaultIncludedBuildController(IncludedBuildState includedBuild, ProjectStateRegistry projectStateRegistry, WorkerLeaseService workerLeaseService) {
        super(includedBuild);
        this.includedBuild = includedBuild;
        this.projectStateRegistry = projectStateRegistry;
        this.parentLease = workerLeaseService.getCurrentWorkerLease();
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    protected void doStartTaskExecution(ExecutorService executorService) {
        executorService.submit(new BuildOpRunnable(CurrentBuildOperationRef.instance().get()));
    }

    @Override
    protected void doAwaitTaskCompletion(Consumer<? super Throwable> executionFailures) {
        // Ensure that this thread does not hold locks while waiting and so prevent this work from completing
        projectStateRegistry.blocking(() -> {
            lock.lock();
            try {
                while (!finished) {
                    awaitStateChange();
                }
                for (Throwable taskFailure : this.executionFailures) {
                    executionFailures.accept(taskFailure);
                }
                this.executionFailures.clear();
            } finally {
                lock.unlock();
            }
        });
    }

    private void run() {
        try {
            workerLeaseService.withSharedLease(parentLease, this::doBuild);
        } catch (Throwable t) {
            executionFailed(t);
        } finally {
            markFinished();
        }
    }

    private void markFinished() {
        lock.lock();
        try {
            finished = true;
            stateChange.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void awaitStateChange() {
        try {
            stateChange.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void doBuild() {
        ExecutionResult<Void> result = includedBuild.getWorkGraph().execute();
        executionFinished(result);
    }

    private void executionFinished(ExecutionResult<Void> result) {
        lock.lock();
        try {
            executionFailures.addAll(result.getFailures());
        } finally {
            lock.unlock();
        }
    }

    private void executionFailed(Throwable failure) {
        lock.lock();
        try {
            executionFailures.add(failure);
        } finally {
            lock.unlock();
        }
    }

    private class BuildOpRunnable implements Runnable {
        private final BuildOperationRef parentBuildOperation;

        BuildOpRunnable(BuildOperationRef parentBuildOperation) {
            this.parentBuildOperation = parentBuildOperation;
        }

        @Override
        public void run() {
            CurrentBuildOperationRef.instance().set(parentBuildOperation);
            try {
                DefaultIncludedBuildController.this.run();
            } finally {
                CurrentBuildOperationRef.instance().set(null);
            }
        }
    }
}
