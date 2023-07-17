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
import org.gradle.tooling.events.download.FileDownloadFinishEvent;
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.FileDownloadProgressEvent;
import org.gradle.tooling.events.download.FileDownloadResult;
import org.gradle.tooling.events.download.FileDownloadStartEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult;
import org.gradle.tooling.events.download.internal.NotFoundFileDownloadSuccessResult;
import org.gradle.tooling.events.internal.DefaultBinaryPluginIdentifier;
import org.gradle.tooling.events.internal.DefaultFinishEvent;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.internal.DefaultScriptPluginIdentifier;
import org.gradle.tooling.events.internal.DefaultStartEvent;
import org.gradle.tooling.events.internal.DefaultStatusEvent;
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent;
import org.gradle.tooling.events.lifecycle.BuildPhaseOperationDescriptor;
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent;
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent;
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseFinishEvent;
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseOperationDescriptor;
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseStartEvent;
import org.gradle.tooling.events.problems.ProblemDescriptor;
import org.gradle.tooling.events.problems.ProblemEvent;
import org.gradle.tooling.events.problems.internal.DefaultProblemEvent;
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor;
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
import org.gradle.tooling.events.test.TestOperationResult;
import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.gradle.tooling.events.test.TestOutputEvent;
import org.gradle.tooling.events.test.internal.DefaultTestFailureResult;
import org.gradle.tooling.events.test.internal.DefaultTestOutputEvent;
import org.gradle.tooling.events.test.internal.DefaultTestOutputOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult;
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
import org.gradle.tooling.internal.consumer.DefaultFileComparisonTestAssertionFailure;
import org.gradle.tooling.internal.consumer.DefaultTestAssertionFailure;
import org.gradle.tooling.internal.consumer.DefaultTestFrameworkFailure;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFileComparisonTestAssertionFailure;
import org.gradle.tooling.internal.protocol.InternalProblemEvent;
import org.gradle.tooling.internal.protocol.InternalTestAssertionFailure;
import org.gradle.tooling.internal.protocol.InternalTestFrameworkFailure;
import org.gradle.tooling.internal.protocol.OperationMapping;
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalBuildPhaseDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalFileDownloadDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalFileDownloadResult;
import org.gradle.tooling.internal.protocol.events.InternalIncrementalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.InternalNotFoundFileDownloadResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalStatusEvent;
import org.gradle.tooling.internal.protocol.events.InternalSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskWithExtraInfoDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestFailureResult;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSkippedResult;
import org.gradle.tooling.internal.protocol.events.InternalTestSuccessResult;
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalWorkItemDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * Converts progress events sent from the tooling provider to the tooling client to the corresponding event types available on the public Tooling API, and broadcasts the converted events to the
 * matching progress listeners. This adapter handles all the different incoming progress event types (except the original logging-derived progress listener).
 */
public class BuildProgressListenerAdapter implements InternalBuildProgressListener {

    private final DescriptorCache descriptorCache = new DescriptorCache();

    private final List<ProgressListenerBroadcaster> broadcasters = new ArrayList<>();

    private final ListenerBroadcast<ProgressListener> testProgressListeners;
    private final ListenerBroadcast<ProgressListener> taskProgressListeners;
    private final ListenerBroadcast<ProgressListener> buildOperationProgressListeners;
    private final ListenerBroadcast<ProgressListener> workItemProgressListeners;
    private final ListenerBroadcast<ProgressListener> projectConfigurationProgressListeners;
    private final ListenerBroadcast<ProgressListener> transformProgressListeners;
    private final ListenerBroadcast<ProgressListener> testOutputProgressListeners;
    private final ListenerBroadcast<ProgressListener> fileDownloadListeners;
    private final ListenerBroadcast<ProgressListener> buildPhaseListeners;
    private final ListenerBroadcast<ProgressListener> problemListeners;

//    private final ListenerBroadcast<ProgressListener> testProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> taskProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> buildOperationProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> workItemProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> projectConfigurationProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> transformProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> testOutputProgressListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> fileDownloadListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> buildPhaseListeners = new ListenerBroadcast<>(ProgressListener.class);
//    private final ListenerBroadcast<ProgressListener> problemListeners = new ListenerBroadcast<>(ProgressListener.class);;

    private final Map<OperationType, ListenerBroadcast<ProgressListener>> listeners = new HashMap<>();

    BuildProgressListenerAdapter(Map<OperationType, List<ProgressListener>> listeners) {

        List<ProgressListener> noListeners = emptyList();

        for (OperationType operationType : OperationMapping.getOperationTypes()) {
            ListenerBroadcast<ProgressListener> listener = this.listeners.get(operationType);
            if(listener == null) {
                listener = new ListenerBroadcast<>(ProgressListener.class);
                this.listeners.put(operationType, listener);
            }
            listener.addAll(listeners.getOrDefault(operationType, noListeners));
        }

        broadcasters.add(new TestProgressBroadcaster(descriptorCache));

        testProgressListeners = this.listeners.get(OperationType.TEST);
        taskProgressListeners = this.listeners.get(OperationType.TASK);
        buildOperationProgressListeners = this.listeners.get(OperationType.GENERIC);
        workItemProgressListeners = this.listeners.get(OperationType.WORK_ITEM);
        projectConfigurationProgressListeners = this.listeners.get(OperationType.PROJECT_CONFIGURATION);
        transformProgressListeners = this.listeners.get(OperationType.TRANSFORM);
        testOutputProgressListeners = this.listeners.get(OperationType.TEST_OUTPUT);
        fileDownloadListeners = this.listeners.get(OperationType.FILE_DOWNLOAD);
        buildPhaseListeners = this.listeners.get(OperationType.BUILD_PHASE);
        problemListeners = this.listeners.get(OperationType.PROBLEMS);

//        testProgressListeners.addAll(listeners.getOrDefault(OperationType.TEST, noListeners));
//        taskProgressListeners.addAll(listeners.getOrDefault(OperationType.TASK, noListeners));
//        buildOperationProgressListeners.addAll(listeners.getOrDefault(OperationType.GENERIC, noListeners));
//        workItemProgressListeners.addAll(listeners.getOrDefault(OperationType.WORK_ITEM, noListeners));
//        projectConfigurationProgressListeners.addAll(listeners.getOrDefault(OperationType.PROJECT_CONFIGURATION, noListeners));
//        transformProgressListeners.addAll(listeners.getOrDefault(OperationType.TRANSFORM, noListeners));
//        testOutputProgressListeners.addAll(listeners.getOrDefault(OperationType.TEST_OUTPUT, noListeners));
//        fileDownloadListeners.addAll(listeners.getOrDefault(OperationType.FILE_DOWNLOAD, noListeners));
//        buildPhaseListeners.addAll(listeners.getOrDefault(OperationType.BUILD_PHASE, noListeners));
//        problemListeners.addAll(listeners.getOrDefault(OperationType.PROBLEMS, noListeners));
    }

    @Override
    public List<String> getSubscribedOperations() {
        List<String> operations = new ArrayList<>();
        for (Map.Entry<OperationType, ListenerBroadcast<ProgressListener>> operationTypeListenerBroadcastEntry : this.listeners.entrySet()) {
            if (!operationTypeListenerBroadcastEntry.getValue().isEmpty()) {
                operations.add(OperationMapping.getOperationName(operationTypeListenerBroadcastEntry.getKey()));
            }
        }

//        if (!testProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.TEST_EXECUTION);
//        }
//        if (!taskProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.TASK_EXECUTION);
//        }
//        if (!buildOperationProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.BUILD_EXECUTION);
//        }
//        if (!workItemProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.WORK_ITEM_EXECUTION);
//        }
//        if (!projectConfigurationProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION);
//        }
//        if (!transformProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.TRANSFORM_EXECUTION);
//        }
//        if (!testOutputProgressListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.TEST_OUTPUT);
//        }
//        if (!fileDownloadListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.FILE_DOWNLOAD);
//        }
//        if (!buildPhaseListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.BUILD_PHASE);
//        }
//        if (!problemListeners.isEmpty()) {
//            operations.add(InternalBuildProgressListener.PROBLEMS);
//        }
        return operations;
    }

    @Override
    public void onEvent(Object event) {
        for (ProgressListenerBroadcaster broadcaster : broadcasters) {
            if(broadcaster.canHandle(event.getClass())) {
                broadcaster.broadCast(event);
                return;
            }
        }

        if (event instanceof ProgressEvent) {
            broadcastProgressEvent((ProgressEvent) event);
        } else if (event instanceof InternalProgressEvent) {
            broadcastInternalProgressEvent((InternalProgressEvent) event);
        } else {
            throw new IllegalArgumentException("Unexpected event type: " + event);
        }
    }

    private void broadcastProgressEvent(ProgressEvent event) {
        for(ProgressListenerBroadcaster broadcaster : broadcasters) {
            if(broadcaster.canHandleProgressEvent(event.getClass())) {
                broadcaster.getListeners().getSource().statusChanged(event);
                return;
            }
        }

        if (event instanceof TaskProgressEvent) {
            taskProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof WorkItemProgressEvent) {
            workItemProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof ProjectConfigurationProgressEvent) {
            projectConfigurationProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TransformProgressEvent) {
            transformProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof TestOutputEvent) {
            testOutputProgressListeners.getSource().statusChanged(event);
        } else if (event instanceof BuildPhaseProgressEvent) {
            buildPhaseListeners.getSource().statusChanged(event);
        } else if (event instanceof ProblemEvent) {
            problemListeners.getSource().statusChanged(event);
        } else {
            // Everything else treat as a generic operation
            buildOperationProgressListeners.getSource().statusChanged(event);
        }
    }

    private void broadcastInternalProgressEvent(InternalProgressEvent progressEvent) {


        InternalOperationDescriptor descriptor = progressEvent.getDescriptor();


//        for(ProgressListenerBroadcaster broadcaster : broadcasters) {
//            if(broadcaster.canHandleDescriptor(descriptor.getClass())) {
//                broadcaster.getListeners().getSource().statusChanged(event);
//                return;
//            }
//        }
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
        } else if (progressEvent instanceof InternalStatusEvent) {
            broadcastStatusEvent((InternalStatusEvent) progressEvent);
        } else if (descriptor instanceof InternalFileDownloadDescriptor) {
            broadcastFileDownloadEvent(progressEvent, (InternalFileDownloadDescriptor) descriptor);
        } else if (descriptor instanceof InternalBuildPhaseDescriptor) {
            broadcastBuildPhaseEvent(progressEvent, (InternalBuildPhaseDescriptor) descriptor);
        } else if (descriptor instanceof InternalProblemDescriptor) {
            broadcastProblemEvent(progressEvent, (InternalProblemDescriptor) descriptor);
        } else {
            broadcastGenericProgressEvent(progressEvent);
        }
    }

    private void broadcastStatusEvent(InternalStatusEvent progressEvent) {
        OperationDescriptor descriptor = descriptorCache.descriptorCache.get(progressEvent.getDescriptor().getId());
        if (descriptor == null) {
            throw new IllegalStateException(String.format("No operation with id %s in progress.", progressEvent.getDescriptor().getId()));
        }
        fileDownloadListeners.getSource().statusChanged(new DefaultStatusEvent(
            progressEvent.getEventTime(),
            descriptor,
            progressEvent.getTotal(),
            progressEvent.getProgress(),
            progressEvent.getUnits()));
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
        TestOutputEvent outputEvent = toTestOutputEvent(event, descriptor);
        if (outputEvent != null) {
            testOutputProgressListeners.getSource().statusChanged(outputEvent);
        }
    }
    private void broadcastProblemEvent(InternalProgressEvent progressEvent, InternalProblemDescriptor descriptor) {
        ProblemEvent problemEvent = toProblemEvent(progressEvent, descriptor);
        if (problemEvent != null) {
            problemListeners.getSource().statusChanged(problemEvent);
        }
    }

    private void broadcastFileDownloadEvent(InternalProgressEvent event, InternalFileDownloadDescriptor descriptor) {
        ProgressEvent progressEvent = toFileDownloadProgressEvent(event, descriptor);
        if (progressEvent != null) {
            fileDownloadListeners.getSource().statusChanged(progressEvent);
        }
    }

    private void broadcastBuildPhaseEvent(InternalProgressEvent event, InternalBuildPhaseDescriptor descriptor) {
        ProgressEvent progressEvent = toBuildPhaseEvent(event, descriptor);
        if (progressEvent != null) {
            buildPhaseListeners.getSource().statusChanged(progressEvent);
        }
    }

    private BuildPhaseProgressEvent toBuildPhaseEvent(InternalProgressEvent event, InternalBuildPhaseDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return buildPhaseStartEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return buildPhaseFinishEvent((InternalOperationFinishedProgressEvent) event);
        } else {
            return null;
        }
    }

    private BuildPhaseStartEvent buildPhaseStartEvent(InternalOperationStartedProgressEvent event, InternalBuildPhaseDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        BuildPhaseOperationDescriptor newDescriptor = descriptorCache.addDescriptor(
            event.getDescriptor(),
            new DefaultBuildPhaseOperationDescriptor(descriptor, parent)
        );
        return new DefaultBuildPhaseStartEvent(event.getEventTime(), event.getDisplayName(), newDescriptor);
    }

    private BuildPhaseFinishEvent buildPhaseFinishEvent(InternalOperationFinishedProgressEvent event) {
        BuildPhaseOperationDescriptor descriptor = descriptorCache.removeDescriptor(BuildPhaseOperationDescriptor.class, event.getDescriptor());
        OperationResult result;
        if (event.getResult() instanceof InternalFailureResult) {
            InternalFailureResult internalResult = (InternalFailureResult) event.getResult();
            result = new DefaultOperationFailureResult(internalResult.getStartTime(), internalResult.getEndTime(), toFailures(internalResult.getFailures()));
        } else {
            result = new DefaultOperationSuccessResult(event.getResult().getStartTime(), event.getResult().getEndTime());
        }
        return new DefaultBuildPhaseFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, result);
    }

    private void broadcastGenericProgressEvent(InternalProgressEvent event) {
        ProgressEvent progressEvent = toGenericProgressEvent(event);
        if (progressEvent != null) {
            buildOperationProgressListeners.getSource().statusChanged(progressEvent);
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

    private FileDownloadProgressEvent toFileDownloadProgressEvent(InternalProgressEvent event, InternalFileDownloadDescriptor descriptor) {
        if (event instanceof InternalOperationStartedProgressEvent) {
            return fileDownloadStartEvent((InternalOperationStartedProgressEvent) event, descriptor);
        } else if (event instanceof InternalOperationFinishedProgressEvent) {
            return fileDownloadFinishedEvent((InternalOperationFinishedProgressEvent) event);
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

    private TestOutputEvent transformTestOutput(InternalTestOutputEvent event, InternalTestOutputDescriptor descriptor) {
        TestOutputDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toTestOutputDescriptor(event, descriptor));
        return new DefaultTestOutputEvent(event.getEventTime(), clientDescriptor);
    }

    private ProblemEvent toProblemEvent(InternalProgressEvent progressEvent, InternalProblemDescriptor descriptor) {
        if (progressEvent instanceof InternalProblemEvent) {
            ProblemDescriptor clientDescriptor = descriptorCache.addDescriptor(progressEvent.getDescriptor(), toProblemDescriptor((InternalProblemEvent) progressEvent, descriptor));
            return new DefaultProblemEvent(progressEvent.getEventTime(), clientDescriptor);
        }
        return null;
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


    private TaskStartEvent taskStartedEvent(InternalOperationStartedProgressEvent event, InternalTaskDescriptor descriptor) {
        TaskOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toTaskDescriptor(descriptor));
        return new DefaultTaskStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private WorkItemStartEvent workItemStartedEvent(InternalOperationStartedProgressEvent event, InternalWorkItemDescriptor descriptor) {
        WorkItemOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toWorkItemDescriptor(descriptor));
        return new DefaultWorkItemStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private ProjectConfigurationStartEvent projectConfigurationStartedEvent(InternalOperationStartedProgressEvent event, InternalProjectConfigurationDescriptor descriptor) {
        ProjectConfigurationOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toProjectConfigurationDescriptor(descriptor));
        return new DefaultProjectConfigurationStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private TransformStartEvent transformStartedEvent(InternalOperationStartedProgressEvent event, InternalTransformDescriptor descriptor) {
        TransformOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toTransformDescriptor(descriptor));
        return new DefaultTransformStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private FileDownloadStartEvent fileDownloadStartEvent(InternalOperationStartedProgressEvent event, InternalFileDownloadDescriptor descriptor) {
        FileDownloadOperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), toFileDownloadDescriptor(descriptor));
        return new DefaultFileDownloadStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private StartEvent genericStartedEvent(InternalOperationStartedProgressEvent event) {
        OperationDescriptor clientDescriptor = descriptorCache.addDescriptor(event.getDescriptor(), descriptorCache.toDescriptor(event.getDescriptor()));
        return new DefaultStartEvent(event.getEventTime(), event.getDisplayName(), clientDescriptor);
    }

    private TaskFinishEvent taskFinishedEvent(InternalOperationFinishedProgressEvent event) {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        TaskOperationDescriptor descriptor = descriptorCache.assertDescriptorType(TaskOperationDescriptor.class, descriptorCache.getParentDescriptor(event.getDescriptor().getId()));
        return new DefaultTaskFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTaskResult((InternalTaskResult) event.getResult()));
    }

    private WorkItemFinishEvent workItemFinishedEvent(InternalOperationFinishedProgressEvent event) {
        WorkItemOperationDescriptor descriptor = descriptorCache.removeDescriptor(WorkItemOperationDescriptor.class, event.getDescriptor());
        return new DefaultWorkItemFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toWorkItemResult(event.getResult()));
    }

    private ProjectConfigurationFinishEvent projectConfigurationFinishedEvent(InternalOperationFinishedProgressEvent event) {
        ProjectConfigurationOperationDescriptor descriptor = descriptorCache.removeDescriptor(ProjectConfigurationOperationDescriptor.class, event.getDescriptor());
        return new DefaultProjectConfigurationFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toProjectConfigurationResult((InternalProjectConfigurationResult) event.getResult()));
    }

    private TransformFinishEvent transformFinishedEvent(InternalOperationFinishedProgressEvent event) {
        // do not remove task descriptors because they might be needed to describe subsequent tasks' dependencies
        TransformOperationDescriptor descriptor = descriptorCache.assertDescriptorType(TransformOperationDescriptor.class, descriptorCache.getParentDescriptor(event.getDescriptor().getId()));
        return new DefaultTransformFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toTransformResult(event.getResult()));
    }

    private FileDownloadFinishEvent fileDownloadFinishedEvent(InternalOperationFinishedProgressEvent event) {
        FileDownloadOperationDescriptor descriptor = descriptorCache.removeDescriptor(FileDownloadOperationDescriptor.class, event.getDescriptor());
        return new DefaultFileDownloadFinishEvent(event.getEventTime(), event.getDisplayName(), descriptor, toFileDownloadResult(event.getResult()));
    }

    private FinishEvent genericFinishedEvent(InternalOperationFinishedProgressEvent event) {
        OperationDescriptor descriptor = descriptorCache.removeDescriptor(OperationDescriptor.class, event.getDescriptor());
        return new DefaultFinishEvent<>(event.getEventTime(), event.getDisplayName(), descriptor, toResult(event.getResult()));
    }

    private TaskOperationDescriptor toTaskDescriptor(InternalTaskDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        if (descriptor instanceof InternalTaskWithExtraInfoDescriptor) {
            InternalTaskWithExtraInfoDescriptor descriptorWithExtras = (InternalTaskWithExtraInfoDescriptor) descriptor;
            Set<OperationDescriptor> dependencies = descriptorCache.collectDescriptors(descriptorWithExtras.getDependencies());
            PluginIdentifier originPlugin = toPluginIdentifier(descriptorWithExtras.getOriginPlugin());
            return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath(), dependencies, originPlugin);
        }
        return new DefaultTaskOperationDescriptor(descriptor, parent, descriptor.getTaskPath());
    }

    private WorkItemOperationDescriptor toWorkItemDescriptor(InternalWorkItemDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        return new DefaultWorkItemOperationDescriptor(descriptor, parent);
    }

    private ProjectConfigurationOperationDescriptor toProjectConfigurationDescriptor(InternalProjectConfigurationDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        return new DefaultProjectConfigurationOperationDescriptor(descriptor, parent);
    }

    private TransformOperationDescriptor toTransformDescriptor(InternalTransformDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        return new DefaultTransformOperationDescriptor(descriptor, parent, descriptorCache.collectDescriptors(descriptor.getDependencies()));
    }

    private FileDownloadOperationDescriptor toFileDownloadDescriptor(InternalFileDownloadDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        return new DefaultFileDownloadOperationDescriptor(descriptor, parent);
    }

    private TestOutputDescriptor toTestOutputDescriptor(InternalTestOutputEvent event, InternalTestOutputDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        Destination destination = Destination.fromCode(event.getResult().getDestination());
        String message = event.getResult().getMessage();
        return new DefaultTestOutputOperationDescriptor(descriptor, parent, destination, message);
    }

    private ProblemDescriptor toProblemDescriptor(InternalProblemEvent progressEvent, InternalProblemDescriptor descriptor) {
        OperationDescriptor parent = descriptorCache.getParentDescriptor(descriptor.getParentId());
        return new DefaultProblemsOperationDescriptor(descriptor, parent, progressEvent.getProblemGroup(), progressEvent.getSeverity(),
            progressEvent.getMessage(), progressEvent.getDescription(), progressEvent.getSolutions(), progressEvent.getPath(),
            progressEvent.getLine(), progressEvent.getColumn(), progressEvent.getDocumentationLink(), progressEvent.getCause(), progressEvent.getProblemType(), progressEvent.getAdditionalMetaData());
    }



    private FileDownloadResult toFileDownloadResult(InternalOperationResult result) {
        InternalFileDownloadResult fileDownloadResult = (InternalFileDownloadResult) result;
        if (result instanceof InternalNotFoundFileDownloadResult) {
            return new NotFoundFileDownloadSuccessResult(result.getStartTime(), result.getEndTime());
        }
        if (result instanceof InternalSuccessResult) {
            return new DefaultFileDownloadSuccessResult(result.getStartTime(), result.getEndTime(), fileDownloadResult.getBytesDownloaded());
        }
        if (result instanceof InternalFailureResult) {
            return new DefaultFileDownloadFailureResult(result.getStartTime(), result.getEndTime(), toFailures(result.getFailures()), fileDownloadResult.getBytesDownloaded());
        }
        return null;
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

    public static TaskOperationResult toTaskResult(InternalTaskResult result) {
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
            return ((InternalTaskCachedResult) result).isFromCache();
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

    public static List<Failure> toFailures(List<? extends InternalFailure> causes) {
        if (causes == null) {
            return null;
        }
        List<Failure> failures = new ArrayList<>();
        for (InternalFailure cause : causes) {
            failures.add(toFailure(cause));
        }
        return failures;
    }

    private static Failure toFailure(InternalFailure origFailure) {
        if (origFailure instanceof InternalTestAssertionFailure) {
            if (origFailure instanceof InternalFileComparisonTestAssertionFailure) {
                InternalTestAssertionFailure assertionFailure = (InternalTestAssertionFailure) origFailure;
                return new DefaultFileComparisonTestAssertionFailure(assertionFailure.getMessage(),
                    assertionFailure.getDescription(),
                    assertionFailure.getExpected(),
                    assertionFailure.getActual(),
                    toFailures(origFailure.getCauses()),
                    ((InternalTestAssertionFailure) origFailure).getClassName(),
                    ((InternalTestAssertionFailure) origFailure).getStacktrace(),
                    ((InternalFileComparisonTestAssertionFailure) origFailure).getExpectedContent(),
                    ((InternalFileComparisonTestAssertionFailure) origFailure).getActualContent()
                );
            }
            InternalTestAssertionFailure assertionFailure = (InternalTestAssertionFailure) origFailure;
            return new DefaultTestAssertionFailure(
                assertionFailure.getMessage(),
                assertionFailure.getDescription(),
                assertionFailure.getExpected(),
                assertionFailure.getActual(),
                toFailures(origFailure.getCauses()),
                ((InternalTestAssertionFailure) origFailure).getClassName(),
                ((InternalTestAssertionFailure) origFailure).getStacktrace()
            );
        } else if (origFailure instanceof InternalTestFrameworkFailure) {
            InternalTestFrameworkFailure frameworkFailure = (InternalTestFrameworkFailure) origFailure;
            return new DefaultTestFrameworkFailure(
                frameworkFailure.getMessage(),
                frameworkFailure.getDescription(),
                toFailures(origFailure.getCauses()),
                ((InternalTestFrameworkFailure) origFailure).getClassName(),
                ((InternalTestFrameworkFailure) origFailure).getStacktrace()
            );
        }
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
