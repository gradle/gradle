/*
 * Copyright 2018 the original author or authors.
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

import javax.annotation.Nullable;

/**
 * Executes the given commands without issuing build operations.
 */
public final class NullBuildOperationExecutor implements BuildOperationExecutor {

    public static final BuildOperationExecutor INSTANCE = new NullBuildOperationExecutor();

    private static final BuildOperationContext CONTEXT = new BuildOperationContext() {
        @Override
        public void failed(@Nullable Throwable failure) {
        }

        @Override
        public void setResult(Object result) {
        }

        @Override
        public void setStatus(String status) {
        }
    };

    private NullBuildOperationExecutor() {
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        buildOperation.run(CONTEXT);
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        return buildOperation.call(CONTEXT);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BuildOperationRef getCurrentOperation() {
        throw new UnsupportedOperationException();
    }

}
