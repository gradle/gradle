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

package org.gradle.internal.operations;

import org.gradle.api.Action;

import java.util.List;

/**
 * A BuildOperationExecutor for tests.
 * Simply execute given operations, does not support current/parent operations.
 */
public class TestBuildOperationExecutor implements BuildOperationExecutor {

    private final TestBuildOperationRunner runner;
    public final TestBuildOperationRunner.Log log;

    public TestBuildOperationExecutor() {
        this(new TestBuildOperationRunner());
    }

    public TestBuildOperationExecutor(TestBuildOperationRunner buildOperationRunner) {
        this.runner = buildOperationRunner;
        this.log = runner.log;
    }

    public List<BuildOperationDescriptor> getOperations() {
        return runner.getOperations();
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        schedulingAction.execute(new TestBuildOperationQueue<O>(runner));
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction) {
        runAllWithAccessToProjectState(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        runAll(schedulingAction);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(worker, schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    public BuildOperationRef getCurrentOperation() {
        throw new UnsupportedOperationException();
    }

    public static class TestBuildOperationQueue<O extends RunnableBuildOperation> implements BuildOperationQueue<O> {

        public final BuildOperationRunner runner;

        public TestBuildOperationQueue() {
            this(new TestBuildOperationRunner());
        }

        private TestBuildOperationQueue(BuildOperationRunner runner) {
            this.runner = runner;
        }

        @Override
        public void add(O operation) {
            runner.run(operation);
        }

        @Override
        public void cancel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void waitForCompletion() throws MultipleBuildOperationFailures {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogLocation(String logLocation) {
            throw new UnsupportedOperationException();
        }
    }

    public void reset() {
        runner.reset();
    }
}
