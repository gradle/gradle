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

import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheStoreCommand;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.execution.IncrementalContext;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UpToDateResult;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector;
import org.gradle.internal.execution.timeout.impl.DefaultTimeoutHandler;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.scopes.ExecutionGradleServices;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror;

import javax.annotation.Nullable;
import java.io.File;

public class WorkExecutorTestFixture {

    private final BuildCacheController buildCacheController = new BuildCacheController() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public boolean isEmitDebugLogging() {
            return false;
        }

        @Nullable
        @Override
        public <T> T load(BuildCacheLoadCommand<T> command) {
            return null;
        }

        @Override
        public void store(BuildCacheStoreCommand command) {

        }

        @Override
        public void close() {

        }
    };
    private BuildInvocationScopeId buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate());
    private BuildCancellationToken cancellationToken = new DefaultBuildCancellationToken();
    private final WorkExecutor<IncrementalContext, UpToDateResult> workExecutor;

    WorkExecutorTestFixture(DefaultFileSystemMirror fileSystemMirror,
                            ExecutionHistoryStore executionHistoryStore) {
        BuildCacheCommandFactory buildCacheCommandFactory = null;
        OutputChangeListener outputChangeListener = new OutputChangeListener() {
            @Override
            public void beforeOutputChange() {
                fileSystemMirror.beforeOutputChange();
            }

            @Override
            public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
                fileSystemMirror.beforeOutputChange(affectedOutputPaths);
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
        workExecutor = new ExecutionGradleServices().createWorkExecutor(
            buildCacheCommandFactory,
            buildCacheController,
            cancellationToken,
            buildInvocationScopeId,
            new DefaultExecutionStateChangeDetector(),
            outputChangeListener,
            outputFilesRepository,
            new DefaultTimeoutHandler(null)
        );
    }

    public WorkExecutor<IncrementalContext, UpToDateResult> getWorkExecutor() {
        return workExecutor;
    }
}
