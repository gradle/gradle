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

import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType;
import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType.Result.AnnotationProcessorDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;
import org.gradle.internal.build.event.types.AbstractTaskResult;
import org.gradle.internal.build.event.types.DefaultAnnotationProcessorResult;
import org.gradle.internal.build.event.types.DefaultJavaCompileTaskSuccessResult;
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult;
import org.gradle.internal.build.event.OperationResultPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaCompileTaskSuccessResultPostProcessor implements OperationResultPostProcessor, BuildOperationListener {

    private static final Object TASK_MARKER = new Object();
    private final Map<Object, CompileJavaBuildOperationType.Result> results = new ConcurrentHashMap<Object, CompileJavaBuildOperationType.Result>();
    private final Map<Object, Object> parentsOfOperationsWithJavaCompileTaskAncestor = new ConcurrentHashMap<Object, Object>();

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationType.Details) {
            parentsOfOperationsWithJavaCompileTaskAncestor.put(buildOperation.getId(), TASK_MARKER);
        } else if (buildOperation.getParentId() != null && parentsOfOperationsWithJavaCompileTaskAncestor.containsKey(buildOperation.getParentId())) {
            parentsOfOperationsWithJavaCompileTaskAncestor.put(buildOperation.getId(), buildOperation.getParentId());
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (finishEvent.getResult() instanceof CompileJavaBuildOperationType.Result) {
            CompileJavaBuildOperationType.Result result = (CompileJavaBuildOperationType.Result) finishEvent.getResult();
            Object taskBuildOperationId = findTaskOperationId(buildOperation.getParentId());
            results.put(taskBuildOperationId, result);
        }
        parentsOfOperationsWithJavaCompileTaskAncestor.remove(buildOperation.getId());
    }

    private Object findTaskOperationId(Object id) {
        Object parent = parentsOfOperationsWithJavaCompileTaskAncestor.get(id);
        if (parent == TASK_MARKER) {
            return id;
        }
        return findTaskOperationId(parent);
    }

    @Override
    public AbstractTaskResult process(AbstractTaskResult taskResult, Object taskBuildOperationId) {
        CompileJavaBuildOperationType.Result compileResult = results.remove(taskBuildOperationId);
        if (taskResult instanceof DefaultTaskSuccessResult && compileResult != null) {
            return new DefaultJavaCompileTaskSuccessResult((DefaultTaskSuccessResult) taskResult, toAnnotationProcessorResults(compileResult.getAnnotationProcessorDetails()));
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
