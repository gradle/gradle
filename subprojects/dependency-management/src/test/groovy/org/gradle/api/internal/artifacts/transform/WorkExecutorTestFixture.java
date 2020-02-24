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

import org.apache.commons.io.FileUtils;
import org.gradle.caching.internal.controller.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector;
import org.gradle.internal.execution.timeout.impl.DefaultTimeoutHandler;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.fingerprint.overlap.impl.DefaultOverlappingOutputDetector;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.TestBuildOperationExecutor;
import org.gradle.internal.scan.config.BuildScanPluginApplied;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.scopes.ExecutionGradleServices;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class WorkExecutorTestFixture {

    private final WorkExecutor<ExecutionRequestContext, CachingResult> workExecutor;

    WorkExecutorTestFixture(
        VirtualFileSystem virtualFileSystem,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter

    ) {
        BuildCacheController buildCacheController = new BuildCacheController() {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public boolean isEmitDebugLogging() {
                return false;
            }

            @Override
            public <T> Optional<T> load(BuildCacheLoadCommand<T> command) {
                return Optional.empty();
            }

            @Override
            public void store(BuildCacheStoreCommand command) {

            }

            @Override
            public void close() {

            }
        };
        BuildInvocationScopeId buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate());
        BuildCancellationToken cancellationToken = new DefaultBuildCancellationToken();
        BuildCacheCommandFactory buildCacheCommandFactory = null;
        OutputChangeListener outputChangeListener = new OutputChangeListener() {
            @Override
            public void beforeOutputChange() {
                virtualFileSystem.invalidateAll();
            }

            @Override
            public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
                virtualFileSystem.update(affectedOutputPaths, () -> {});
            }
        };
        OutputFilesRepository outputFilesRepository = new OutputFilesRepository() {
            @Override
            public boolean isGeneratedByGradle(File file) {
                return true;
            }

            @Override
            public void recordOutputs(Iterable<? extends FileSystemSnapshot> outputFileFingerprints) {
            }
        };
        BuildScanPluginApplied buildScanPluginApplied = new BuildScanPluginApplied() {
            @Override
            public boolean isBuildScanPluginApplied() {
                return false;
            }
        };
        Deleter deleter = new Deleter() {
            @Override
            public boolean deleteRecursively(File target) {
                return deleteRecursively(target, false);
            }

            @Override
            public boolean deleteRecursively(File target, boolean followSymlinks) {
                return FileUtils.deleteQuietly(target);
            }

            @Override
            public boolean ensureEmptyDirectory(File target) throws IOException {
                return ensureEmptyDirectory(target, false);
            }

            @Override
            public boolean ensureEmptyDirectory(File target, boolean followSymlinks) throws IOException {
                File[] children = target.listFiles();
                FileUtils.forceDelete(target);
                FileUtils.forceMkdir(target);
                return children == null || children.length == 0;
            }

            @Override
            public boolean delete(File target) throws IOException {
                if (!target.exists()) {
                    return false;
                }
                FileUtils.forceDelete(target);
                return true;
            }
        };
        workExecutor = new ExecutionGradleServices().createWorkExecutor(
            buildCacheCommandFactory,
            buildCacheController,
            cancellationToken,
            buildInvocationScopeId,
            new TestBuildOperationExecutor(),
            buildScanPluginApplied,
            classLoaderHierarchyHasher,
            deleter,
            new DefaultExecutionStateChangeDetector(),
            outputChangeListener,
            outputFilesRepository,
            new DefaultOverlappingOutputDetector(),
            new DefaultTimeoutHandler(null),
            behaviour -> DeprecationLogger.deprecateBehaviour(behaviour).willBeRemovedInGradle7().undocumented().nagUser(),
            valueSnapshotter
        );
    }

    public WorkExecutor<ExecutionRequestContext, CachingResult> getWorkExecutor() {
        return workExecutor;
    }
}
