/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.buildtree.BuildTreeFinishExecutor;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.operations.lifecycle.FinishRootBuildTreeBuildOperationType;

import javax.annotation.Nullable;
import java.util.List;

public class OperationFiringBuildTreeFinishExecutor implements BuildTreeFinishExecutor {

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildTreeFinishExecutor delegate;

    public OperationFiringBuildTreeFinishExecutor(BuildOperationExecutor buildOperationExecutor, BuildTreeFinishExecutor delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public RuntimeException finishBuildTree(List<Throwable> failures) {
        return buildOperationExecutor.call(new CallableBuildOperation<RuntimeException>() {
            @Override
            public RuntimeException call(BuildOperationContext context) {
                try {
                    return delegate.finishBuildTree(failures);
                } finally {
                    context.setResult(RESULT);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Finish root build tree")
                    .details(DETAILS);
            }
        });
    }

    private static final FinishRootBuildTreeBuildOperationType.Details DETAILS = new FinishRootBuildTreeBuildOperationType.Details() {};
    private static final FinishRootBuildTreeBuildOperationType.Result RESULT = new FinishRootBuildTreeBuildOperationType.Result() {};
}
