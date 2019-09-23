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
import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.StartEvent;
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult.PluginApplicationResult;
import org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent;
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent;
import org.gradle.tooling.events.configuration.internal.DefaultPluginApplicationResult;
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationFailureResult;
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationFinishEvent;
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationOperationDescriptor;
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationStartEvent;
import org.gradle.tooling.events.configuration.internal.DefaultProjectConfigurationSuccessResult;
import org.gradle.tooling.events.internal.DefaultBinaryPluginIdentifier;
import org.gradle.tooling.events.internal.DefaultFinishEvent;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.internal.DefaultScriptPluginIdentifier;
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
import org.gradle.tooling.events.task.internal.TaskExecutionDetails;
import org.gradle.tooling.events.task.internal.java.DefaultAnnotationProcessorResult;
import org.gradle.tooling.events.task.internal.java.DefaultJavaCompileTaskSuccessResult;
import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult.AnnotationProcessorResult;
import org.gradle.tooling.events.test.Destination;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationResult;
import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.gradle.tooling.events.test.TestOutputEvent;
import org.gradle.tooling.events.test.TestProgressEvent;
import org.gradle.tooling.events.test.TestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultJvmTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult;
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent;
import org.gradle.tooling.events.test.internal.DefaultTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestOutputEvent;
import org.gradle.tooling.events.test.internal.DefaultTestOutputOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult;
import org.gradle.tooling.events.test.internal.DefaultTestStartEvent;
import org.gradle.tooling.events.test.internal.DefaultTestSuccessResult;
import org.gradle.tooling.events.transform.TransformFinishEvent;
import org.gradle.tooling.events.transform.TransformOperationDescriptor;
import org.gradle.tooling.events.transform.TransformOperationResult;
import org.gradle.tooling.events.transform.TransformProgressEvent;
import org.gradle.tooling.events.transform.TransformStartEvent;
import org.gradle.tooling.events.transform.internal.DefaultTransformFailureResult;
import org.gradle.tooling.events.transform.internal.DefaultTransformFinishEvent;
import org.gradle.tooling.events.transform.internal.DefaultTransformOperationDescriptor;
import org.gradle.tooling.events.transform.internal.DefaultTransformStartEvent;
import org.gradle.tooling.events.transform.internal.DefaultTransformSuccessResult;
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
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalIncrementalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskWithExtraInfoDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTestFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTestStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalWorkItemDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
public class BuildProgressListenerAdapter implements InternalBuildProgressListener {

    private final ListenerBroadcast<ProgressListener> testProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> taskProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> buildOperationProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> workItemProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> projectConfigurationProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> transformProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final ListenerBroadcast<ProgressListener> testOutputProgressListeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final Map<Object, OperationDescriptor> descriptorCache = new HashMap<Object, OperationDescriptor>();

    BuildProgressListenerAdapter(Map<OperationType, List<ProgressListener>> listeners) {
        List<ProgressListener> noListeners = Collections.emptyList();
        testProgressListeners.addAll(listeners.getOrDefault(OperationType.TEST, noListeners));
        taskProgressListeners.addAll(listeners.getOrDefault(OperationType.TASK, noListeners));
        buildOperationProgressListeners.addAll(listeners.getOrDefault(OperationType.GENERIC, noListeners));
        workItemProgressListeners.addAll(listeners.getOrDefault(OperationType.WORK_ITEM, noListeners));
        projectConfigurationProgressListeners.addAll(listeners.getOrDefault(OperationType.PROJECT_CONFIGURATION, noListeners));
        transformProgressListeners.addAll(listeners.getOrDefault(OperationType.TRANSFORM, noListeners));
        testOutputProgressListeners.addAll(listeners.getOrDefault(OperationType.TEST_OUTPUT, noListeners));
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
        if (!projectConfigurationProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION);
        }
        if (!transformProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.TRANSFORM_EXECUTION);
        }
        if (!testOutputProgressListeners.isEmpty()) {
            operations.add(InternalBuildProgressListener.TEST_OUTPUT);
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
            broadcastTestProgressEvent((InternalTestProgressEvent) event);
        } else if (event instanceof InternalProgressEvent) {
            broadcastInternalProgressEvent((InternalProgressEvent) event);
        }
    }

    private void broadcastProgressEvent(ProgressEvent event) {
        if (event instanceof TestProgressEvent) {
            testProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TaskProgressEvent) {
            taskProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof WorkItemProgressEvent) {
            workItemProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof ProjectConfigurationProgressEvent) {
            projectConfigurationProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TransformProgressEvent) {
            transformProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TestOutputEvent) {
            testOutputProgressListeners.getSource().statusChanged(event);
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

    private void broadcastInternalProgressEvent(InternalProgressEvent progressEvent) {
        InternalOperationDescriptor descriptor = progressEvent.getDescriptor();
        if (descriptor instanceof InternalTaskDescriptor) {
            broadcastTaskProgressEvent(progressEvent, (InternalTaskDescriptor) descriptor);
        } else if (descriptor instanceof InternalWorkItemDescriptor) {
            broadcastWorkItemProgressEvent(progressEvent, (InternalWorkItemDescriptor) descriptor);
        } else if (descriptor instanceof InternalProjectConfigurationDescriptor) {
            broadcastProjectConfigurationProgressEvent(progressEvent, (InternalProjectConfigurationDescriptor) descriptor);
        } else if (descriptor instanceof InternalTransformDescriptor) {
            broadcastTransformProgressEvent(progressEvent, (InternalTransformDescriptor) descriptor);
        } else if (descriptor instanceof InternalTestOutputDescriptor) {
            broadcastTestOutputEvent(progressEvent, (InternalTestOutputDescriptor) descriptor);
        } else {
            broadcastGenericProgressEvent(progressEvent);
        }
    }

    private void broadcastTaskProgressEvent(InternalProgressEvent event, InternalTaskDescriptor descriptor) {
        TaskProgressEvent taskProgressEvent = toTaskProgressEvent(event, descriptor);
        if (taskProgressEvent != null) {
            taskProgressListeners.getSource().statusChanged(taskProgressEvent);
        }
    }

    private void broadcastWorkItemProgressEvent(InternalProgressEvent event, InternalWorkItemDescriptor descriptor) {
        WorkItemProgressEvent workItemProgressEvent = toWorkItemProgressEvent(event, descriptor);
        if (workItemProgressEvent != null) {
            workItemProgressListeners.getSource().statusChanged(workItemProgressEvent);
        }
    }

    private void broadcastProjectConfigurationProgressEvent(InternalProgressEvent event, InternalProjectConfigurationDescriptor descriptor) {
        ProjectConfigurationProgressEvent projectConfigurationProgressEvent = toProjectConfigurationProgressEvent(event, descriptor);
        if (projectConfigurationProgressEvent != null) {
            projectConfigurationProgressListeners.getSource().statusChanged(projectConfigurationProgressEvent);
        }
    }

    private void broadcastTransformProgressEvent(InternalProgressEvent event, InternalTransformDescriptor descriptor) {
        TransformProgressEvent transformProgressEvent = toTransformProgressEvent(event, descriptor);
        if (transformProgressEvent != null) {
            transformProgressListeners.getSource().statusChanged(transformProgressEvent);
        }
    }

    private void broadcastTestOutputEvent(InternalProgressEvent event, InternalTestOutputDescriptor descriptor) {
        // Everything else treat as a generic operation
        TestOutputEvent outputEvent = toTestOutputEvent(event, descriptor);
        if (outputEvent != null) {
            testOutputProgressListeners.getSource().statusChanged(outputEvent);
        }
    }

    private void broadcastGenericProgressEvent(InternalProgressEvent event) {
        ProgressEvent progressEvent = toGenericProgressEvent(event);
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

    private TaskProgressEvent toTaskProgressEvent(InternalProgressEvent event, InternalTaskDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return taskStartedEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return taskFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private WorkItemProgressEvent toWorkItemProgressEvent(InternalProgressEvent event, InternalWorkItemDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return workItemStartedEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return workItemFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private ProjectConfigurationProgressEvent toProjectConfigurationProgressEvent(InternalProgressEvent event, InternalProjectConfigurationDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return projectConfigurationStartedEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return projectConfigurationFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TransformProgressEvent toTransformProgressEvent(InternalProgressEvent event, InternalTransformDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return transformStartedEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return transformFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TestOutputEvent toTestOutputEvent(InternalProgressEvent event, InternalTestOutputDescriptor descriptor) {
        if (event instanceof InternalTestOutputEvent) {
            return transformTestOutput((InternalTestOutputEvent) event, descriptor);
        } else {
            return null;
        }
    }

    private ProgressEvent toGenericProgressEvent(InternalProgressEvent event) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return genericStartedEvent((InternalOperationStartedProgressEvent) event);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return genericFinishedEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private TestStartEvent testStartedEvent(InternalTestStartedProgressEvent event) {
        TestOperationDescriptor testDescriptor = addDescriptor(event.getDescriptor(), toTestDescriptor(event.getDescriptor()));
        return new DefaultTestStartEvent(event.getEventTime(), event.getDisplayName(), testDescriptor);
    }

    private TaskStartEvent taskStartedEvent(InternalOperationStartedProgressEvent event, InternalTaskDescriptor descriptor) {
        TaskOperationDescriptor taskDescriptor = addDescriptor(event.getDescriptor(), toTaskDescriptor(descriptor));
        return new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), taskDescriptor);
    }

    private WorkItemStartEvent workItemStartedEvent(InternalOperationStartedProgressEvent event, InternalWorkItemDescriptor descriptor) {
        WorkItemOperationDescriptor workItemDescriptor = addDescriptor(event.getDescriptor(), toWorkItemDescriptor(descriptor));
        return new DefaultWorkItemStartEvent(event.getEventTime(), event.getDisplayName(), workItemDescriptor);
    }

    private ProjectConfigurationStartEvent projectConfigurationStartedEvent(InternalOperationStartedProgressEvent event, InternalProjectConfigurationDescriptor descriptor) {
        ProjectConfigurationOperationDescriptor projectConfigurationDescriptor = addDescriptor(event.getDescriptor(), toProjectConfigurationDescriptor(descriptor));
        return new DefaultProjectConfigurationStartEvent(event.getEventTime(), event.getDisplayName(), projectConfigurationDescriptor);
    }

    private TransformStartEvent transformStartedEvent(InternalOperationStartedProgressEvent event, InternalTransformDescriptor descriptor) {
        TransformOperationDescriptor projectConfigurationDescriptor = addDescriptor(event.getDescriptor(), toTransformDescriptor(descriptor));
        return new DefaultTransformStartEvent(event.getEventTime(), event.getDisplayName(), projectConfigurationDescriptor);
    }

    private TestOutputEvent transformTestOutput(InternalTestOutputEvent event, InternalTestOutputDescriptor descriptor) {
        TestOutputDescriptor outputDescriptor = addDescriptor(event.getDescriptor(), toTestOutputDescriptor(descriptor));
        Destination destination = Destination.fromCode(event.getResult().getDestination());
        return new DefaultTestOutputEvent(event.getEventTime(), outputDescriptor, destination, event.getResult().getMessage());
    }

    private StartEvent genericStartedEvent(InternalOperationStartedProgressEvent event) {
        OperationDescriptor descriptor = addDescriptor(event.getDescriptor(), toDescriptor(event.getDescriptor()));
        return new DefaultStartEvent(event.getEventTime(), event.getDisplayName(), descriptor);
    }

    private TestFinishEvent testFinishedEvent(InternalTestFinishedProgressEvent event) {
        TestOperationDescriptor descriptor = removeDescriptor(TestOperationDescriptor.class, event.getDescriptor());
        return new DefaultTestFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTestResult(event.getResult()));
    }

    private TaskFinishEvent taskFinishedEvent(InternalOperationFinishedProgressEvent event) {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        TaskOperationDescriptor descriptor = assertDescriptorType(TaskOperationDescriptor.class, getParentDescriptor(event.getDescriptor().getId()));
        return new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTaskResult((InternalTaskResult) event.getResult()));
    }

    private WorkItemFinishEvent workItemFinishedEvent(InternalOperationFinishedProgressEvent event) {
        WorkItemOperationDescriptor descriptor = removeDescriptor(WorkItemOperationDescriptor.class, event.getDescriptor());
        return new DefaultWorkItemFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toWorkItemResult(event.getResult()));
    }

    private ProjectConfigurationFinishEvent projectConfigurationFinishedEvent(InternalOperationFinishedProgressEvent event) {
        ProjectConfigurationOperationDescriptor descriptor = removeDescriptor(ProjectConfigurationOperationDescriptor.class, event.getDescriptor());
        return new DefaultProjectConfigurationFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toProjectConfigurationResult((InternalProjectConfigurationResult) event.getResult()));
    }

    private TransformFinishEvent transformFinishedEvent(InternalOperationFinishedProgressEvent event) {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        TransformOperationDescriptor descriptor = assertDescriptorType(TransformOperationDescriptor.class, getParentDescriptor(event.getDescriptor().getId()));
        return new DefaultTransformFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTransformResult(event.getResult()));
    }

    private FinishEvent genericFinishedEvent(InternalOperationFinishedProgressEvent event) {
        OperationDescriptor descriptor = removeDescriptor(OperationDescriptor.class, event.getDescriptor());
        return new DefaultFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toResult(event.getResult()));
    }

    private synchronized <T extends OperationDescriptor> T addDescriptor(InternalOperationDescriptor descriptor, T clientDescriptor) {
        if (this.descriptorCache.containsKey(descriptor.getId())) {
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
        if (descriptor instanceof InternalTaskWithExtraInfoDescriptor) {
            InternalTaskWithExtraInfoDescriptor descriptorWithExtras = (InternalTaskWithExtraInfoDescriptor) descriptor;
            Set<OperationDescriptor> dependencies = collectDescriptors(descriptorWithExtras.getDependencies());
            PluginIdentifier originPlugin = toPluginIdentifier(descriptorWithExtras.getOriginPlugin());
            return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath(), dependencies, originPlugin);
        }
        return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath());
    }

    private WorkItemOperationDescriptor toWorkItemDescriptor(InternalWorkItemDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultWorkItemOperationDescriptor(descriptor, parent);
    }

    private ProjectConfigurationOperationDescriptor toProjectConfigurationDescriptor(InternalProjectConfigurationDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultProjectConfigurationOperationDescriptor(descriptor, parent);
    }

    private TransformOperationDescriptor toTransformDescriptor(InternalTransformDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultTransformOperationDescriptor(descriptor, parent, collectDescriptors(descriptor.getDependencies()));
    }

    private TestOutputDescriptor toTestOutputDescriptor(InternalOperationDescriptor descriptor) {
        OperationDescriptor parent = getParentDescriptor(descriptor.getParentId());
        return new DefaultTestOutputOperationDescriptor(descriptor, parent);
    }

    private Set<OperationDescriptor> collectDescriptors(Set<? extends InternalOperationDescriptor> dependencies) {
        Set<OperationDescriptor> result = new LinkedHashSet<OperationDescriptor>();
        for (InternalOperationDescriptor dependency : dependencies) {
            OperationDescriptor dependencyDescriptor = descriptorCache.get(dependency.getId());
            if (dependencyDescriptor != null) {
                result.add(dependencyDescriptor);
            }
        }
        return result;
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
        if (result instanceof InternalTaskSuccessResult) {
            InternalTaskSuccessResult successResult = (InternalTaskSuccessResult) result;
            if (result instanceof InternalJavaCompileTaskOperationResult) {
                List<AnnotationProcessorResult> annotationProcessorResults = toAnnotationProcessorResults(((InternalJavaCompileTaskOperationResult) result).getAnnotationProcessorResults());
                return new DefaultJavaCompileTaskSuccessResult(result.getStartTime(), result.getEndTime(), successResult.isUpToDate(), isFromCache(result), toTaskExecutionDetails(result), annotationProcessorResults);
            }
            return new DefaultTaskSuccessResult(result.getStartTime(), result.getEndTime(), successResult.isUpToDate(), isFromCache(result), toTaskExecutionDetails(result));
        } else if (result instanceof InternalTaskSkippedResult) {
            return new DefaultTaskSkippedResult(result.getStartTime(), result.getEndTime(), ((InternalTaskSkippedResult) result).getSkipMessage());
        } else if (result instanceof InternalTaskFailureResult) {
            return new DefaultTaskFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()), toTaskExecutionDetails(result));
        } else {
            return null;
        }
    }

    private static boolean isFromCache(InternalTaskResult result) {
        if (result instanceof InternalTaskCachedResult) {
            return ((InternalTaskCachedResult)result).isFromCache();
        }
        return false;
    }

    private static TaskExecutionDetails toTaskExecutionDetails(InternalTaskResult result) {
        if (result instanceof InternalIncrementalTaskResult) {
            InternalIncrementalTaskResult taskResult = (InternalIncrementalTaskResult) result;
            return TaskExecutionDetails.of(taskResult.isIncremental(), taskResult.getExecutionReasons());
        }
        return TaskExecutionDetails.unsupported();
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

    private static ProjectConfigurationOperationResult toProjectConfigurationResult(InternalProjectConfigurationResult result) {
        if (result instanceof InternalSuccessResult) {
            return new DefaultProjectConfigurationSuccessResult(result.getStartTime(), result.getEndTime(), toPluginApplicationResults(result.getPluginApplicationResults()));
        } else if (result instanceof InternalFailureResult) {
            return new DefaultProjectConfigurationFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()), toPluginApplicationResults(result.getPluginApplicationResults()));
        } else {
            return null;
        }
    }

    private static List<? extends PluginApplicationResult> toPluginApplicationResults(List<? extends InternalPluginApplicationResult> pluginApplicationResults) {
        List<PluginApplicationResult> results = new ArrayList<PluginApplicationResult>();
        for (InternalPluginApplicationResult result : pluginApplicationResults) {
            PluginIdentifier plugin = toPluginIdentifier(result.getPlugin());
            if (plugin != null) {
                results.add(new DefaultPluginApplicationResult(plugin, result.getTotalConfigurationTime()));
            }
        }
        return results;
    }

    private static PluginIdentifier toPluginIdentifier(InternalPluginIdentifier pluginIdentifier) {
        if (pluginIdentifier instanceof InternalBinaryPluginIdentifier) {
            InternalBinaryPluginIdentifier binaryPlugin = (InternalBinaryPluginIdentifier) pluginIdentifier;
            return new DefaultBinaryPluginIdentifier(binaryPlugin.getDisplayName(), binaryPlugin.getClassName(), binaryPlugin.getPluginId());
        } else if (pluginIdentifier instanceof InternalScriptPluginIdentifier) {
            InternalScriptPluginIdentifier scriptPlugin = (InternalScriptPluginIdentifier) pluginIdentifier;
            return new DefaultScriptPluginIdentifier(scriptPlugin.getDisplayName(), scriptPlugin.getUri());
        } else {
            return null;
        }
    }

    private static TransformOperationResult toTransformResult(InternalOperationResult result) {
        if (result instanceof InternalSuccessResult) {
            return new DefaultTransformSuccessResult(result.getStartTime(), result.getEndTime());
        } else if (result instanceof InternalFailureResult) {
            return new DefaultTransformFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()));
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
