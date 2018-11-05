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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.Collections;
import java.util.Map;

public class BuildOperationReportingCompiler<T extends CompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final TaskInternal task;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationReportingCompiler(TaskInternal task, Compiler<T> delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.task = task;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public WorkResult execute(final T spec) {
        return buildOperationExecutor.call(new CallableBuildOperation<WorkResult>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Invoke compiler for " + task.getIdentityPath())
                    .details(new Details(task));
            }

            @Override
            public WorkResult call(BuildOperationContext context) {
                WorkResult result = delegate.execute(spec);
                context.setResult(toBuildOperationResult(result));
                return result;
            }

            private Result toBuildOperationResult(WorkResult result) {
                if (result instanceof CompilationWithAnnotationProcessingResult) {
                    AnnotationProcessingResult annotationProcessingResult = ((CompilationWithAnnotationProcessingResult) result).getAnnotationProcessingResult();
                    return new Result(annotationProcessingResult.getExecutionTimeByProcessor());
                }
                return new Result(Collections.<String, Long>emptyMap());
            }
        });
    }

    private static class Details implements CompileWithAnnotationProcessingBuildOperationType.Details {
        private final TaskInternal task;

        Details(TaskInternal task) {
            this.task = task;
        }

        @Override
        public String getTaskPath() {
            return task.getTaskIdentity().projectPath.toString();
        }
    }

    private static class Result implements CompileWithAnnotationProcessingBuildOperationType.Result {
        private final Map<String, Long> executionTimeByAnnotationProcessor;

        Result(Map<String, Long> executionTimeByAnnotationProcessor) {
            this.executionTimeByAnnotationProcessor = executionTimeByAnnotationProcessor;
        }

        @Override
        public Map<String, Long> getExecutionTimeByAnnotationProcessor() {
            return executionTimeByAnnotationProcessor;
        }
    }
}
