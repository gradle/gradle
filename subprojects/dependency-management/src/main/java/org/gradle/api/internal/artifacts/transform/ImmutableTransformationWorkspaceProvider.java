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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.Try;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Closeable;
import java.io.File;

import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

@NotThreadSafe
public class ImmutableTransformationWorkspaceProvider implements TransformationWorkspaceProvider, Closeable {
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final SingleDepthFileAccessTracker fileAccessTracker;
    private final File baseDirectory;
    private final ExecutionHistoryStore executionHistoryStore;
    private final PersistentCache cache;

    public ImmutableTransformationWorkspaceProvider(File baseDirectory, CacheRepository cacheRepository, FileAccessTimeJournal fileAccessTimeJournal, ExecutionHistoryStore executionHistoryStore) {
        this.baseDirectory = baseDirectory;
        this.cache = cacheRepository
            .cache(baseDirectory)
            .withCleanup(createCleanupAction(baseDirectory, fileAccessTimeJournal))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Artifact transforms cache")
            .withLockOptions(mode(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
        this.fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDirectory, FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);
        this.executionHistoryStore = executionHistoryStore;
    }

    private CleanupAction createCleanupAction(File baseDirectory, FileAccessTimeJournal fileAccessTimeJournal) {
        return CompositeCleanupAction.builder()
            .add(baseDirectory, new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES))
            .build();
    }

    @Override
    public ExecutionHistoryStore getExecutionHistoryStore() {
        return executionHistoryStore;
    }

    @Override
    public Try<ImmutableList<File>> withWorkspace(TransformationWorkspaceIdentity identity, TransformationWorkspaceAction workspaceAction) {
        return cache.withFileLock(() -> {
            String workspacePath = identity.getIdentity();
            File workspaceDir = new File(baseDirectory, workspacePath);
            fileAccessTracker.markAccessed(workspaceDir);
            return workspaceAction.useWorkspace(workspacePath, workspaceDir);
        });
    }

    @Override
    public void close() {
        cache.close();
    }
}
