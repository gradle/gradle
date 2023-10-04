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

package org.gradle.workers.internal;

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CallableBuildOperation;

public abstract class AbstractWorker implements BuildOperationAwareWorker {

    public static final Result RESULT = new Result();

    private final BuildOperationExecutor buildOperationExecutor;

    AbstractWorker(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec) {
        return execute(spec, buildOperationExecutor.getCurrentOperation());
    }

    DefaultWorkResult executeWrappedInBuildOperation(final IsolatedParametersActionExecutionSpec<?> spec, final BuildOperationRef parentBuildOperation, final Work work) {
        return buildOperationExecutor.call(new CallableBuildOperation<DefaultWorkResult>() {
            @Override
            public DefaultWorkResult call(BuildOperationContext context) {
                DefaultWorkResult result = work.execute(spec);
                context.setResult(RESULT);
                context.failed(result.getException());
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(spec.getDisplayName())
                    .parent(parentBuildOperation)
                    .details(new Details(spec.getActionImplementationClassName(), spec.getDisplayName()));
            }
        });
    }

    interface Work {
        DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec);
    }

    static class Details implements ExecuteWorkItemBuildOperationType.Details {

        private final String className;
        private final String displayName;

        public Details(String className, String displayName) {
            this.className = className;
            this.displayName = displayName;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

    }

    static class Result implements ExecuteWorkItemBuildOperationType.Result {
    }

}
