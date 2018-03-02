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

package org.gradle.internal.progress;

import org.gradle.api.Action;
import org.gradle.internal.operations.BuildOperation;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.BuildOperationWorker;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;

public class DelegatingBuildOperationExecutor implements BuildOperationExecutor {

    private final BuildOperationExecutor delegate;

    public DelegatingBuildOperationExecutor(BuildOperationExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        delegate.run(buildOperation);
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        return delegate.call(buildOperation);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        delegate.runAll(schedulingAction);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        delegate.runAll(worker, schedulingAction);
    }

    @Override
    public BuildOperationState getCurrentOperation() {
        return delegate.getCurrentOperation();
    }
}
