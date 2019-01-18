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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolve the input state of a task before it is being executed.
 *
 * This snapshot is required for up-to-date checks, caching and incremental execution.
 */
public class ResolveBeforeExecutionStateTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveBeforeExecutionStateTaskExecuter.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final TaskFingerprinter taskFingerprinter;
    private final TaskExecuter delegate;

    public ResolveBeforeExecutionStateTaskExecuter(
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        TaskFingerprinter taskFingerprinter,
        TaskExecuter delegate
    ) {
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.taskFingerprinter = taskFingerprinter;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (context.getTaskExecutionMode().isTaskHistoryMaintained()) {
            context.setBeforeExecutionState(createExecutionState(task, context.getTaskProperties(), context.getAfterPreviousExecution(), context.getOutputFilesBeforeExecution()));
        }
        return delegate.execute(task, state, context);
    }

    private BeforeExecutionState createExecutionState(TaskInternal task, TaskProperties properties, @Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = ImplementationSnapshot.of(taskClass, classLoaderHierarchyHasher);
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        @SuppressWarnings("RedundantTypeArguments")
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = afterPreviousExecutionState == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : afterPreviousExecutionState.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, properties, previousInputProperties, valueSnapshotter);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = taskFingerprinter.fingerprintTaskFiles(task, properties.getInputFileProperties());

        return new DefaultBeforeExecutionState(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            inputFiles,
            outputFiles
        );
    }

    private static ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImplementations = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            actionImplementations.add(taskAction.getActionImplementation(classLoaderHierarchyHasher));
        }
        return actionImplementations.build();
    }

    private static ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, TaskProperties properties, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        Map<String, Object> inputPropertyValues = properties.getInputPropertyValues().create();
        assert inputPropertyValues != null;
        for (Map.Entry<String, Object> entry : inputPropertyValues.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            try {
                ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    builder.put(propertyName, valueSnapshotter.snapshot(value, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.", task, propertyName, value), e);
            }
        }

        return builder.build();
    }
}
