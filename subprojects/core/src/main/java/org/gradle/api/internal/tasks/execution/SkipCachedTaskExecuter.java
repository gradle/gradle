/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.command.BuildCacheLoadListener;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.packaging.CacheableTree;
import org.gradle.caching.internal.packaging.UnrecoverableUnpackingException;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.SortedSet;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final BuildCacheController buildCache;
    private final TaskExecuter delegate;
    private final TaskOutputChangesListener taskOutputChangesListener;
    private final BuildCacheCommandFactory commandFactory;

    public SkipCachedTaskExecuter(
        BuildCacheController buildCache,
        TaskOutputChangesListener taskOutputChangesListener,
        BuildCacheCommandFactory commandFactory,
        TaskExecuter delegate
    ) {
        this.taskOutputChangesListener = taskOutputChangesListener;
        this.commandFactory = commandFactory;
        this.buildCache = buildCache;
        this.delegate = delegate;
    }

    @Override
    public void execute(final TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.debug("Determining if {} is cached already", task);

        TaskProperties taskProperties = context.getTaskProperties();
        TaskOutputCachingBuildCacheKey cacheKey = context.getBuildCacheKey();
        boolean taskOutputCachingEnabled = state.getTaskOutputCaching().isEnabled();

        SortedSet<CacheableTree> outputProperties = null;
        if (taskOutputCachingEnabled) {
            if (task.isHasCustomActions()) {
                LOGGER.info("Custom actions are attached to {}.", task);
            }
            final TaskArtifactState taskState = context.getTaskArtifactState();
            // TODO: This is really something we should do at an earlier/higher level so that the input and output
            // property values are locked in at this point.
            outputProperties = resolveProperties(taskProperties.getOutputFileProperties());
            if (taskState.isAllowedToUseCachedResults()) {
                try {
                    OriginMetadata originMetadata = buildCache.load(
                        commandFactory.createLoad(cacheKey, outputProperties, task, taskProperties.getLocalStateFiles(), new BuildCacheLoadListener() {
                            @Override
                            public void beforeLoad() {
                                taskOutputChangesListener.beforeTaskOutputChanged();
                            }

                            @Override
                            public void afterLoad(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshots, OriginMetadata originMetadata) {
                                taskState.snapshotAfterLoadedFromCache(snapshots, originMetadata);
                            }

                            @Override
                            public void afterLoad(Throwable error) {
                                taskState.afterOutputsRemovedBeforeTask();
                            }
                        })
                    );
                    if (originMetadata != null) {
                        state.setOutcome(TaskExecutionOutcome.FROM_CACHE);
                        context.setOriginMetadata(originMetadata);
                        return;
                    }
                } catch (UnrecoverableUnpackingException e) {
                    // We didn't manage to recover from the unpacking error, there might be leftover
                    // garbage among the task's outputs, thus we must fail the build
                    throw e;
                } catch (Exception e) {
                    // There was a failure during downloading, previous task outputs should bu unaffected
                    LOGGER.warn("Failed to load cache entry for {}, falling back to executing task", task, e);
                }
            } else {
                LOGGER.info("Not loading {} from cache because loading from cache is disabled for this task", task);
            }
        }

        delegate.execute(task, state, context);

        if (taskOutputCachingEnabled) {
            if (state.getFailure() == null) {
                try {
                    TaskArtifactState taskState = context.getTaskArtifactState();
                    Map<String, CurrentFileCollectionFingerprint> outputFingerprints = taskState.getOutputFingerprints();
                    buildCache.store(commandFactory.createStore(cacheKey, outputProperties, outputFingerprints, task, context.getExecutionTime()));
                } catch (Exception e) {
                    LOGGER.warn("Failed to store cache entry {}", cacheKey.getDisplayName(), e);
                }
            } else {
                LOGGER.debug("Not storing result of {} in cache because the task failed", task);
            }
        }
    }

    private static SortedSet<CacheableTree> resolveProperties(ImmutableSortedSet<? extends TaskOutputFilePropertySpec> properties) {
        ImmutableSortedSet.Builder<CacheableTree> builder = ImmutableSortedSet.naturalOrder();
        for (TaskOutputFilePropertySpec property : properties) {
            // At this point we assume that the task only has cacheable properties,
            // otherwise caching would have been disabled by now
            builder.add(new TaskOutputTree((CacheableTaskOutputFilePropertySpec) property));
        }
        return builder.build();
    }

    private static class TaskOutputTree implements CacheableTree {
        private final CacheableTaskOutputFilePropertySpec property;

        public TaskOutputTree(CacheableTaskOutputFilePropertySpec property) {
            this.property = property;
        }

        @Override
        public String getName() {
            return property.getPropertyName();
        }

        @Override
        public Type getType() {
            switch (property.getOutputType()) {
                case FILE:
                    return Type.FILE;
                case DIRECTORY:
                    return Type.DIRECTORY;
                default:
                    throw new AssertionError();
            }
        }

        @Nullable
        @Override
        public File getRoot() {
            return property.getOutputFile();
        }

        @Override
        public int compareTo(@Nonnull CacheableTree o) {
            return getName().compareTo(o.getName());
        }
    }
}
