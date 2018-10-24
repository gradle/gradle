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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.changes.Util;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
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
            private boolean previousExecutionLoadAttempted;
            private HistoricalTaskExecution previousExecution;
            private CurrentTaskExecution currentExecution;

            @Override
            public HistoricalTaskExecution getPreviousExecution() {
                if (!previousExecutionLoadAttempted) {
                    previousExecutionLoadAttempted = true;
                    previousExecution = loadPreviousExecution(task);
                }
                return previousExecution;
            }

            @Override
            public CurrentTaskExecution getCurrentExecution() {
                if (currentExecution == null) {
                    currentExecution = createExecution(task, taskProperties, getPreviousExecution());
                }
                return currentExecution;
            }

            @Override
            public void persist(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata) {
                CurrentTaskExecution execution = getCurrentExecution();
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

    private CurrentTaskExecution createExecution(TaskInternal task, TaskProperties taskProperties, @Nullable HistoricalTaskExecution previousExecution) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = ImplementationSnapshot.of(taskClass, classLoaderHierarchyHasher);
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        @SuppressWarnings("RedundantTypeArguments")
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, taskProperties, previousInputProperties, valueSnapshotter);

        ImmutableSortedSet<String> outputPropertyNames = getOutputPropertyNamesForCacheKey(taskProperties);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = Util.fingerprintTaskFiles(task, taskProperties.getInputFileProperties(), fingerprinterRegistry);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles = Util.fingerprintTaskFiles(task, taskProperties.getOutputFileProperties(), fingerprinterRegistry);

        return new CurrentTaskExecution(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            outputPropertyNames,
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
    private HistoricalTaskExecution loadPreviousExecution(TaskInternal task) {
        AfterPreviousExecutionState execution = executionHistoryStore.load(task.getPath());
        if (execution == null) {
            return null;
        }
        return new HistoricalTaskExecution(
            execution.getImplementation(),
            execution.getAdditionalImplementations(),
            execution.getInputProperties(),
            execution.getInputFileProperties(),
            execution.getOutputFileProperties(),
            execution.isSuccessful(),
            execution.getOriginMetadata()
        );
    }

    private static ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey(TaskProperties taskProperties) {
        ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties = taskProperties.getOutputFileProperties();
        List<String> outputPropertyNames = Lists.newArrayListWithCapacity(fileProperties.size());
        for (TaskOutputFilePropertySpec propertySpec : fileProperties) {
            if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                CacheableTaskOutputFilePropertySpec cacheablePropertySpec = (CacheableTaskOutputFilePropertySpec) propertySpec;
                if (cacheablePropertySpec.getOutputFile() != null) {
                    outputPropertyNames.add(propertySpec.getPropertyName());
                }
            }
        }
        return ImmutableSortedSet.copyOf(outputPropertyNames);
    }
}
