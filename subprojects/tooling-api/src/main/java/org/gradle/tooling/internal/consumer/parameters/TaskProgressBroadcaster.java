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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskStartEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.events.task.internal.DefaultTaskSkippedResult;
import org.gradle.tooling.events.task.internal.DefaultTaskStartEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskSuccessResult;
import org.gradle.tooling.events.task.internal.TaskExecutionDetails;
import org.gradle.tooling.events.task.internal.java.DefaultAnnotationProcessorResult;
import org.gradle.tooling.events.task.internal.java.DefaultJavaCompileTaskSuccessResult;
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalIncrementalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskWithExtraInfoDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@NonNullApi
public class TaskProgressBroadcaster extends ProgressListenerBroadcasterAdapter implements ProgressListenerBroadcaster{
    private final DescriptorCache descriptorCache;

    public TaskProgressBroadcaster(DescriptorCache descriptorCache, List<ProgressListener> listener) {
        super(TaskProgressEvent.class, InternalTaskDescriptor.class, null, listener);
        this.descriptorCache = descriptorCache;
    }

    @Override
    public void broadCastInterProgressEvent(InternalProgressEvent progressEvent) {
        broadcastTaskProgressEvent(progressEvent, (InternalTaskDescriptor) progressEvent.getDescriptor());
//        listeners.getSource().onProgress(progressEvent);
    }

    private void broadcastTaskProgressEvent(InternalProgressEvent event, InternalTaskDescriptor descriptor) {
        TaskProgressEvent taskProgressEvent = toTaskProgressEvent(event, descriptor);
        if (taskProgressEvent != null) {
            getListeners().getSource().statusChanged(taskProgressEvent);
        }
    }

    private TaskProgressEvent toTaskProgressEvent(InternalProgressEvent event, InternalTaskDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return taskStartedEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return taskFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TaskStartEvent taskStartedEvent(InternalOperationStartedProgressEvent event, InternalTaskDescriptor descriptor) {
        TaskOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toTaskDescriptor(descriptor));
        return new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private TaskFinishEvent taskFinishedEvent(InternalOperationFinishedProgressEvent event) {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        TaskOperationDescriptor descriptor = descriptorCache.assertDescriptorType(TaskOperationDescriptor.class, descriptorCache.getParentDescriptor(event.getDescriptor().getId()));
        return new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTaskResult((InternalTaskResult) event.getResult()));
    }

    private TaskOperationDescriptor toTaskDescriptor(InternalTaskDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        if (descriptor instanceof InternalTaskWithExtraInfoDescriptor) {
            InternalTaskWithExtraInfoDescriptor descriptorWithExtras = (InternalTaskWithExtraInfoDescriptor) descriptor;
            Set<OperationDescriptor> dependencies = descriptorCache.collectDescriptors(descriptorWithExtras.getDependencies());
            PluginIdentifier originPlugin = BuildProgressListenerAdapter.toPluginIdentifier(descriptorWithExtras.getOriginPlugin());
            return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath(), dependencies, originPlugin);
        }
        return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath());
    }

    public static TaskOperationResult toTaskResult(InternalTaskResult result) {
        if (result instanceof InternalTaskSuccessResult) {
            InternalTaskSuccessResult successResult = (InternalTaskSuccessResult) result;
            if (result instanceof InternalJavaCompileTaskOperationResult) {
                List<JavaCompileTaskOperationResult.AnnotationProcessorResult> annotationProcessorResults = toAnnotationProcessorResults(((InternalJavaCompileTaskOperationResult) result).getAnnotationProcessorResults());
                return new DefaultJavaCompileTaskSuccessResult(result.getStartTime(), result.getEndTime(), successResult.isUpToDate(), isFromCache(result), toTaskExecutionDetails(result), annotationProcessorResults);
            }
            return new DefaultTaskSuccessResult(result.getStartTime(), result.getEndTime(), successResult.isUpToDate(), isFromCache(result), toTaskExecutionDetails(result));
        } else if (result instanceof InternalTaskSkippedResult) {
            return new DefaultTaskSkippedResult(result.getStartTime(), result.getEndTime(), ((InternalTaskSkippedResult) result).getSkipMessage());
        } else if (result instanceof InternalTaskFailureResult) {
            return new DefaultTaskFailureResult(result.getStartTime(), result.getEndTime(), ProgressListenerBroadcasterAdapter.toFailures(result.getFailures()), toTaskExecutionDetails(result));
        } else {
            return null;
        }
    }

    private static TaskExecutionDetails toTaskExecutionDetails(InternalTaskResult result) {
        if (result instanceof InternalIncrementalTaskResult) {
            InternalIncrementalTaskResult taskResult = (InternalIncrementalTaskResult) result;
            return TaskExecutionDetails.of(taskResult.isIncremental(), taskResult.getExecutionReasons());
        }
        return TaskExecutionDetails.unsupported();
    }
    private static List<JavaCompileTaskOperationResult.AnnotationProcessorResult> toAnnotationProcessorResults(List<InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult> protocolResults) {
        if (protocolResults == null) {
            return null;
        }
        List<JavaCompileTaskOperationResult.AnnotationProcessorResult> results = new ArrayList<JavaCompileTaskOperationResult.AnnotationProcessorResult>();
        for (InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult result : protocolResults) {
            results.add(toAnnotationProcessorResult(result));
        }
        return results;
    }

    private static JavaCompileTaskOperationResult.AnnotationProcessorResult toAnnotationProcessorResult(InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult result) {
        return new DefaultAnnotationProcessorResult(result.getClassName(), toAnnotationProcessorResultType(result.getType()), result.getDuration());
    }

    private static JavaCompileTaskOperationResult.AnnotationProcessorResult.Type toAnnotationProcessorResultType(String type) {
        if (type.equals(InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_AGGREGATING)) {
            return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.AGGREGATING;
        }
        if (type.equals(InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult.TYPE_ISOLATING)) {
            return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.ISOLATING;
        }
        return JavaCompileTaskOperationResult.AnnotationProcessorResult.Type.UNKNOWN;
    }

    private static boolean isFromCache(InternalTaskResult result) {
        if (result instanceof InternalTaskCachedResult) {
            return ((InternalTaskCachedResult) result).isFromCache();
        }
        return false;
    }
}
