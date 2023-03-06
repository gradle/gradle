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

package org.gradle.api.internal.tasks.compile.tooling;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType;
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.types.AbstractTaskResult;
import org.gradle.internal.build.event.types.DefaultAnnotationProcessorResult;
import org.gradle.internal.build.event.types.DefaultJavaCompileTaskSuccessResult;
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaCompileTaskSuccessResultPostProcessor implements OperationResultPostProcessor {
    private static final Logger LOGGER = Logging.getLogger(JavaCompileTaskSuccessResultPostProcessor.class);

    private final Map<String, CompileJavaBuildOperationType.Result> results = new ConcurrentHashMap<>();

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (finishEvent.getResult() instanceof CompileJavaBuildOperationType.Result) {
            CompileJavaBuildOperationType.Result result = (CompileJavaBuildOperationType.Result) finishEvent.getResult();
            CompileJavaBuildOperationType.Details details = (CompileJavaBuildOperationType.Details) buildOperation.getDetails();
            if (details == null) {
                throw new IllegalStateException("No details for " + buildOperation.getDisplayName() + ", which is required for proper result tracking");
            }
            results.put(details.getTaskIdentityPath(), result);
        }
    }

    @Override
    public AbstractTaskResult process(AbstractTaskResult taskResult, TaskInternal taskInternal) {
        CompileJavaBuildOperationType.Result compileResult = results.remove(taskInternal.getIdentityPath().getPath());
        if (taskResult instanceof DefaultTaskSuccessResult) {
            if (compileResult != null) {
                return new DefaultJavaCompileTaskSuccessResult((DefaultTaskSuccessResult) taskResult, toAnnotationProcessorResults(compileResult.getAnnotationProcessorDetails()));
            } else if (taskInternal instanceof JavaCompile) {
                LOGGER.info("No compile result for " + taskInternal.getIdentityPath());
            }
        }
        return taskResult;
    }

    private List<InternalAnnotationProcessorResult> toAnnotationProcessorResults(List<AnnotationProcessorDetails> allDetails) {
        if (allDetails == null) {
            return null;
        }
        List<InternalAnnotationProcessorResult> results = new ArrayList<InternalAnnotationProcessorResult>(allDetails.size());
        for (AnnotationProcessorDetails details : allDetails) {
            results.add(toAnnotationProcessorResult(details));
        }
        return results;
    }

    private InternalAnnotationProcessorResult toAnnotationProcessorResult(AnnotationProcessorDetails details) {
        return new DefaultAnnotationProcessorResult(details.getClassName(), toAnnotationProcessorType(details.getType()), Duration.ofMillis(details.getExecutionTimeInMillis()));
    }

    private String toAnnotationProcessorType(AnnotationProcessorDetails.Type type) {
        switch (type) {
            case AGGREGATING:
                return InternalAnnotationProcessorResult.TYPE_AGGREGATING;
            case ISOLATING:
                return InternalAnnotationProcessorResult.TYPE_ISOLATING;
            case UNKNOWN:
                return InternalAnnotationProcessorResult.TYPE_UNKNOWN;
        }
        throw new IllegalArgumentException("Missing conversion for enum constant " + type);
    }
}
