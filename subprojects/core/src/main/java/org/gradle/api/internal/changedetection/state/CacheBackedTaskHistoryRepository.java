/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.changes.TaskFingerprintUtil;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@NonNullApi
public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBackedTaskHistoryRepository.class);

    private final ExecutionHistoryStore executionHistoryStore;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;

    public CacheBackedTaskHistoryRepository(
        ExecutionHistoryStore executionHistoryStore,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry
    ) {
        this.executionHistoryStore = executionHistoryStore;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.fingerprinterRegistry = fingerprinterRegistry;
    }

    @Override
    public History getHistory(final TaskInternal task, final TaskProperties taskProperties) {
        return new History() {
            private boolean afterPreviousExecutionStateLoadAttempted;
            private AfterPreviousExecutionState afterPreviousExecutionState;
            private BeforeExecutionState beforeExecutionState;

            @Override
            public AfterPreviousExecutionState getAfterPreviousExecutionState() {
                if (!afterPreviousExecutionStateLoadAttempted) {
                    afterPreviousExecutionStateLoadAttempted = true;
                    afterPreviousExecutionState = loadPreviousExecution(task);
                }
                return afterPreviousExecutionState;
            }

            @Override
            public BeforeExecutionState getBeforeExecutionState() {
                if (beforeExecutionState == null) {
                    beforeExecutionState = createExecution(task, taskProperties, getAfterPreviousExecutionState());
                }
                return beforeExecutionState;
            }

            @Override
            public void persist(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata) {
                BeforeExecutionState execution = getBeforeExecutionState();
                executionHistoryStore.store(
                    task.getPath(),
                    OriginMetadata.fromPreviousBuild(originMetadata.getBuildInvocationId(), originMetadata.getExecutionTime()),
                    execution.getImplementation(),
                    execution.getAdditionalImplementations(),
                    execution.getInputProperties(),
                    execution.getInputFileProperties(),
                    newOutputFingerprints,
                    successful
                );
            }
        };
    }

    private BeforeExecutionState createExecution(TaskInternal task, TaskProperties taskProperties, @Nullable AfterPreviousExecutionState afterPreviousExecutionState) {
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
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, taskProperties, previousInputProperties, valueSnapshotter);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = TaskFingerprintUtil.fingerprintTaskFiles(task, taskProperties.getInputFileProperties(), fingerprinterRegistry);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles = TaskFingerprintUtil.fingerprintTaskFiles(task, taskProperties.getOutputFileProperties(), fingerprinterRegistry);

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

    private static ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, TaskProperties taskProperties, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        Map<String, Object> inputPropertyValues = taskProperties.getInputPropertyValues().create();
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

    @Nullable
    private AfterPreviousExecutionState loadPreviousExecution(TaskInternal task) {
        return executionHistoryStore.load(task.getPath());
    }
}
