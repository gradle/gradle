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
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.OperationResultDecoratorFactory;
import org.gradle.tooling.internal.protocol.events.DefaultAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.DefaultJavaCompileTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskSuccessResult.InternalAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaCompileTaskSuccessResultDecoratorFactory implements OperationResultDecoratorFactory, BuildOperationListener {

    private final Map<Object, CompileJavaBuildOperationType.Result> results = new ConcurrentHashMap<Object, CompileJavaBuildOperationType.Result>();

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof CompileJavaBuildOperationType.Details) {
            Object taskBuildOperationId = ((CompileJavaBuildOperationType.Details) buildOperation.getDetails()).getTaskBuildOperationId();
            results.put(taskBuildOperationId, (CompileJavaBuildOperationType.Result) finishEvent.getResult());
        }
    }

    @Override
    public InternalTaskResult decorate(InternalTaskResult result, Object taskBuildOperationId) {
        CompileJavaBuildOperationType.Result compileResult = results.remove(taskBuildOperationId);
        if (result instanceof InternalTaskSuccessResult && compileResult != null) {
            return new DefaultJavaCompileTaskSuccessResult((InternalTaskSuccessResult) result, toAnnotationProcessorResults(compileResult.getAnnotationProcessorDetails()));
        }
        return result;
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
