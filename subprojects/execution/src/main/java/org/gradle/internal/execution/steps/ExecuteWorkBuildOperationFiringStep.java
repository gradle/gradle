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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.operations.execution.ExecuteWorkBuildOperationType;

import java.util.List;

public class ExecuteWorkBuildOperationFiringStep<C extends IdentityContext, R extends CachingResult> extends BuildOperationStep<C, R> implements Step<C, R> {

    private final Step<? super C, R> delegate;

    public ExecuteWorkBuildOperationFiringStep(BuildOperationExecutor buildOperationExecutor, Step<C, R> delegate) {
        super(buildOperationExecutor);
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return operation(operationContext -> {
                R result = delegate.execute(work, context);
                ExecuteWorkBuildOperationType.Result operationResult = new ExecuteWorkResult(result.getExecutionReasons());
                operationContext.setResult(operationResult);
                return result;
            },
            BuildOperationDescriptor
                .displayName("Execute Unit of Work")
                .details(new ExecuteWorkDetails(work, context)));
    }

    private class ExecuteWorkDetails implements ExecuteWorkBuildOperationType.Details {

        private final UnitOfWork work;
        private final C context;

        public ExecuteWorkDetails(UnitOfWork work, C context) {
            this.work = work;
            this.context = context;
        }

        @Override
        public String getWorkType() {
            return work.getWorkType();
        }

        @Override
        public String getWorkspaceId() {
            return context.getIdentity().getUniqueId();
        }

    }

    private static class ExecuteWorkResult implements ExecuteWorkBuildOperationType.Result {

        private final List<String> executionReasons;

        public ExecuteWorkResult(List<String> executionReasons) {
            this.executionReasons = executionReasons;
        }

        @Override
        public List<String> getExecutionReasons() {
            return executionReasons;
        }
    }
}
