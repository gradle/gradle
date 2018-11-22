/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.StartEvent;
import org.gradle.tooling.events.internal.DefaultFinishEvent;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.internal.DefaultStartEvent;
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
import org.gradle.tooling.events.task.internal.java.DefaultAnnotationProcessorResult;
import org.gradle.tooling.events.task.internal.java.DefaultJavaCompileTaskSuccessResult;
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult.AnnotationProcessorResult;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationResult;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.gradle.tooling.events.test.TestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultJvmTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult;
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent;
import org.gradle.tooling.events.test.internal.DefaultTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult;
import org.gradle.tooling.events.test.internal.DefaultTestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultTestSuccessResult;
import org.gradle.tooling.events.work.WorkItemFinishEvent;
import org.gradle.tooling.events.work.WorkItemOperationDescriptor;
import org.gradle.tooling.events.work.WorkItemOperationResult;
import org.gradle.tooling.events.work.WorkItemProgressEvent;
import org.gradle.tooling.events.work.WorkItemStartEvent;
import org.gradle.tooling.events.work.internal.DefaultWorkItemFailureResult;
import org.gradle.tooling.events.work.internal.DefaultWorkItemFinishEvent;
import org.gradle.tooling.events.work.internal.DefaultWorkItemOperationDescriptor;
import org.gradle.tooling.events.work.internal.DefaultWorkItemStartEvent;
import org.gradle.tooling.events.work.internal.DefaultWorkItemSuccessResult;
import org.gradle.tooling.internal.consumer.DefaultFailure;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.events.InternalFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalWorkItemDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
public class BuildProgressListenerAdapter implements InternalBuildProgressListener {

    private final ListenerBroadcast<ProgressListener> testProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> taskProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> buildOperationProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> workItemProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final Map<Object, OperationDescriptor> descriptorCache = new HashMap<Object, OperationDescriptor>();

    BuildProgressListenerAdapter(Map<OperationType, List<ProgressListener>> listeners) {
        List<ProgressListener> noListeners = Collections.emptyList();
        testProgressListeners.addAll(listeners.getOrDefault(OperationType.TEST, noListeners));
        taskProgressListeners.addAll(listeners.getOrDefault(OperationType.TASK, noListeners));
        buildOperationProgressListeners.addAll(listeners.getOrDefault(OperationType.GENERIC, noListeners));
        workItemProgressListeners.addAll(listeners.getOrDefault(OperationType.WORK_ITEM, noListeners));
    }

    @Override
    public List<String> getSubscribedOperations() {
        List<String> operations = new ArrayList<String>();
        if (!testProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.TEST_EXECUTION);
        }
        if (!taskProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.TASK_EXECUTION);
        }
        if (!buildOperationProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.BUILD_EXECUTION);
        }
        if (!workItemProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.WORK_ITEM_EXECUTION);
        }
        return operations;
    }

    @Override
    public void onEvent(Object event) {
        doBroadcast(event);
    }

    private void doBroadcast(Object event) {
        if (event instanceof ProgressEvent) {
            broadcastProgressEvent((ProgressEvent) event);
        } else if (event instanceof InternalTestProgressEvent) {
            // Special case for events defined prior to InternalProgressEvent
            InternalTestProgressEvent progressEvent = (InternalTestProgressEvent) event;
            broadcastTestProgressEvent(progressEvent);
        } else if (event instanceof InternalProgressEvent) {
            InternalProgressEvent progressEvent = (InternalProgressEvent) event;
            if (progressEvent.getDescriptor() instanceof InternalTaskDescriptor) {
                broadcastTaskProgressEvent(progressEvent);
            } else if (progressEvent.getDescriptor() instanceof InternalWorkItemDescriptor) {
                broadcastWorkItemProgressEvent(progressEvent);
            } else {
                // Everything else treat as a generic operation
                broadcastProgressEvent(progressEvent);
            }
        }
    }

    private void broadcastProgressEvent(ProgressEvent event) {
        if (event instanceof TestProgressEvent) {
            testProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TaskProgressEvent) {
            taskProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof WorkItemProgressEvent) {
            workItemProgressListeners.getSource().statusChanged(event);
        } else {
            // Everything else treat as a generic operation
            buildOperationProgressListeners.getSource().statusChanged(event);
        }
    }

    private void broadcastTestProgressEvent(InternalTestProgressEvent event) {
        TestProgressEvent testProgressEvent = toTestProgressEvent(event);
        if (testProgressEvent != null) {
            testProgressListeners.getSource().statusChanged(testProgressEvent);
        }
    }

    private void broadcastTaskProgressEvent(InternalProgressEvent event) {
        TaskProgressEvent taskProgressEvent = toTaskProgressEvent(event);
        if (taskProgressEvent != null) {
            taskProgressListeners.getSource().statusChanged(taskProgressEvent);
        }
    }

    private void broadcastWorkItemProgressEvent(InternalProgressEvent event) {
        WorkItemProgressEvent workItemProgressEvent = toWorkItemProgressEvent(event);
        if (workItemProgressEvent != null) {
            workItemProgressListeners.getSource().statusChanged(workItemProgressEvent);
        }
    }

    private void broadcastProgressEvent(InternalProgressEvent event) {
        ProgressEvent progressEvent = toProgressEvent(event);
        if (progressEvent != null) {
            buildOperationProgressListeners.getSource().statusChanged(progressEvent);
        }
    }

    private TestProgressEvent toTestProgressEvent(InternalTestProgressEvent event) {
        if (event instanceof InternalTestStartedProgressEvent) {
            return testStartedEvent((InternalTestStartedProgressEvent) event);
        } else if (event instanceof InternalTestFinishedProgressEvent) {
            return testFinishedEvent((InternalTestFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TaskProgressEvent toTaskProgressEvent(InternalProgressEvent event) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return taskStartedEvent((InternalOperationStartedProgressEvent) event);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return taskFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private WorkItemProgressEvent toWorkItemProgressEvent(InternalProgressEvent event) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return workItemStartedEvent((InternalOperationStartedProgressEvent) event);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return workItemFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private ProgressEvent toProgressEvent(InternalProgressEvent event) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return startedEvent((InternalOperationStartedProgressEvent) event);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return finishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TestStartEvent testStartedEvent(InternalTestStartedProgressEvent event) {
        TestOperationDescriptor testDescriptor = addDescriptor(event.getDescriptor(), toTestDescriptor(event.getDescriptor()));
        return new DefaultTestStartEvent(event.getEventTime(), event.getDisplayName(), testDescriptor);
    }

    private TaskStartEvent taskStartedEvent(InternalOperationStartedProgressEvent event) {
        TaskOperationDescriptor descriptor = addDescriptor(event.getDescriptor(), toTaskDescriptor((InternalTaskDescriptor) event.getDescriptor()));
        return new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), descriptor);
    }

    private WorkItemStartEvent workItemStartedEvent(InternalOperationStartedProgressEvent event) {
        WorkItemOperationDescriptor descriptor = addDescriptor(event.getDescriptor(), toWorkItemDescriptor((InternalWorkItemDescriptor) event.getDescriptor()));
        return new DefaultWorkItemStartEvent(event.getEventTime(), event.getDisplayName(), descriptor);
    }

    private StartEvent startedEvent(InternalOperationStartedProgressEvent event) {
        OperationDescriptor descriptor = addDescriptor(event.getDescriptor(), toDescriptor(event.getDescriptor()));
        return new DefaultStartEvent(event.getEventTime(), event.getDisplayName(), descriptor);
    }

    private TestFinishEvent testFinishedEvent(InternalTestFinishedProgressEvent event) {
        TestOperationDescriptor descriptor = removeDescriptor(TestOperationDescriptor.class, event.getDescriptor());
        return new DefaultTestFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTestResult(event.getResult()));
    }

    private TaskFinishEvent taskFinishedEvent(InternalOperationFinishedProgressEvent event) {
        TaskOperationDescriptor descriptor = removeDescriptor(TaskOperationDescriptor.class, event.getDescriptor());
        return new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTaskResult((InternalTaskResult) event.getResult()));
    }

    private WorkItemFinishEvent workItemFinishedEvent(InternalOperationFinishedProgressEvent event) {
        WorkItemOperationDescriptor descriptor = removeDescriptor(WorkItemOperationDescriptor.class, event.getDescriptor());
        return new DefaultWorkItemFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toWorkItemResult(event.getResult()));
    }

    private FinishEvent finishedEvent(InternalOperationFinishedProgressEvent event) {
        OperationDescriptor descriptor = removeDescriptor(OperationDescriptor.class, event.getDescriptor());
        return new DefaultFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toResult(event.getResult()));
    }

    private synchronized <T extends OperationDescriptor> T addDescriptor(InternalOperationDescriptor descriptor, T clientDescriptor) {
        OperationDescriptor cached = this.descriptorCache.get(descriptor.getId());
        if (cached != null) {
            throw new IllegalStateException(String.format("Operation %s already available.", descriptor));
        }
        descriptorCache.put(descriptor.getId(), clientDescriptor);
        return clientDescriptor;
    }

    private synchronized <T extends OperationDescriptor> T removeDescriptor(Class<T> type, InternalOperationDescriptor descriptor) {
        OperationDescriptor cachedTestDescriptor = this.descriptorCache.remove(descriptor.getId());
        if (cachedTestDescriptor == null) {
            throw new IllegalStateException(String.format("Operation %s is not available.", descriptor));
        }
        return assertDescriptorType(type, cachedTestDescriptor);
    }

    private <T extends OperationDescriptor> T assertDescriptorType(Class<T> type, OperationDescriptor descriptor) {
        Class<? extends OperationDescriptor> descriptorClass = descriptor.getClass();
        if (!type.isAssignableFrom(descriptorClass)) {
            throw new IllegalStateException(String.format("Unexpected operation type. Required %s but found %s", type.getName(), descriptorClass.getName()));
        }
        return (T) descriptor;
    }

    private TestOperationDescriptor toTestDescriptor(InternalTestDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        if (descriptor instanceof InternalJvmTestDescriptor) {
            InternalJvmTestDescriptor jvmTestDescriptor = (InternalJvmTestDescriptor) descriptor;
            return new DefaultJvmTestOperationDescriptor(jvmTestDescriptor, parent,
                toJvmTestKind(jvmTestDescriptor.getTestKind()), jvmTestDescriptor.getSuiteName(), jvmTestDescriptor.getClassName(), jvmTestDescriptor.getMethodName());
        } else {
            return new DefaultTestOperationDescriptor(descriptor, parent);
        }
    }

    private static JvmTestKind toJvmTestKind(String testKind) {
        if (InternalJvmTestDescriptor.KIND_SUITE.equals(testKind)) {
            return JvmTestKind.SUITE;
        } else if (InternalJvmTestDescriptor.KIND_ATOMIC.equals(testKind)) {
            return JvmTestKind.ATOMIC;
        } else {
            return JvmTestKind.UNKNOWN;
        }
    }

    private TaskOperationDescriptor toTaskDescriptor(InternalTaskDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultTaskOperationDescriptor(descriptor, descriptor.getTaskPath(), parent);
    }

    private WorkItemOperationDescriptor toWorkItemDescriptor(InternalWorkItemDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultWorkItemOperationDescriptor(descriptor, parent);
    }

    private OperationDescriptor toDescriptor(InternalOperationDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultOperationDescriptor(descriptor, parent);
    }

    private synchronized OperationDescriptor getParentDescriptor(Object parentId) {
        if (parentId == null) {
            return null;
        } else {
            OperationDescriptor operationDescriptor = descriptorCache.get(parentId);
            if (operationDescriptor == null) {
                throw new IllegalStateException(String.format("Parent operation with id %s not available.", parentId));
            } else {
                return operationDescriptor;
            }
        }
    }

    private TestOperationResult toTestResult(InternalTestResult result) {
        if (result instanceof InternalTestSuccessResult) {
            return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestSkippedResult) {
            return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalTestFailureResult) {
            return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()));
        } else {
            return null;
        }
    }

    private static TaskOperationResult toTaskResult(InternalTaskResult result) {
        boolean fromCache = false;
        if (result instanceof InternalTaskCachedResult) {
            fromCache = ((InternalTaskCachedResult)result).isFromCache();
        }

        if (result instanceof InternalTaskSuccessResult) {
            if (result instanceof InternalJavaCompileTaskOperationResult) {
                List<AnnotationProcessorResult> annotationProcessorResults = toAnnotationProcessorResults(((InternalJavaCompileTaskOperationResult) result).getAnnotationProcessorResults());
                return new DefaultJavaCompileTaskSuccessResult(result.getStartTime(), result.getEndTime(), ((InternalTaskSuccessResult) result).isUpToDate(), fromCache, annotationProcessorResults);
            }
            return new DefaultTaskSuccessResult(result.getStartTime(), result.getEndTime(), ((InternalTaskSuccessResult) result).isUpToDate(), fromCache);
        } else if (result instanceof InternalTaskSkippedResult) {
            return new DefaultTaskSkippedResult(result.getStartTime(), result.getEndTime(), ((InternalTaskSkippedResult) result).getSkipMessage());
        } else if (result instanceof InternalTaskFailureResult) {
            return new DefaultTaskFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()));
        } else {
            return null;
        }
    }

    private static WorkItemOperationResult toWorkItemResult(InternalOperationResult result) {
        if (result instanceof InternalSuccessResult) {
            return new DefaultWorkItemSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalFailureResult) {
            return new DefaultWorkItemFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()));
        } else {
            return null;
        }
    }

    private static OperationResult toResult(InternalOperationResult result) {
        if (result instanceof InternalSuccessResult) {
            return new DefaultOperationSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalFailureResult) {
            return new DefaultOperationFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()));
        } else {
            return null;
        }
    }

    private static List<Failure> toFailures(List<? extends InternalFailure> causes) {
        if (causes == null) {
            return null;
        }
        List<Failure> failures = new ArrayList<Failure>();
        for (InternalFailure cause : causes) {
            failures.add(toFailure(cause));
        }
        return failures;
    }

    private static Failure toFailure(InternalFailure origFailure) {
        return origFailure == null ? null : new DefaultFailure(
            origFailure.getMessage(),
            origFailure.getDescription(),
            toFailures(origFailure.getCauses()));
    }

    private static List<AnnotationProcessorResult> toAnnotationProcessorResults(List<InternalAnnotationProcessorResult> protocolResults) {
        if (protocolResults == null) {
            return null;
        }
        List<AnnotationProcessorResult> results = new ArrayList<AnnotationProcessorResult>();
        for (InternalAnnotationProcessorResult result : protocolResults) {
            results.add(toAnnotationProcessorResult(result));
        }
        return results;
    }

    private static AnnotationProcessorResult toAnnotationProcessorResult(InternalAnnotationProcessorResult result) {
        return new DefaultAnnotationProcessorResult(result.getClassName(), toAnnotationProcessorResultType(result.getType()), result.getDuration());
    }

    private static AnnotationProcessorResult.Type toAnnotationProcessorResultType(String type) {
        if (type.equals(InternalAnnotationProcessorResult.TYPE_AGGREGATING)) {
            return AnnotationProcessorResult.Type.AGGREGATING;
        }
        if (type.equals(InternalAnnotationProcessorResult.TYPE_ISOLATING)) {
            return AnnotationProcessorResult.Type.ISOLATING;
        }
        return AnnotationProcessorResult.Type.UNKNOWN;
    }
}
