/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;

public class AssignImmutableWorkspaceStep<C extends IdentityContext> implements Step<C, WorkspaceResult> {
    private final FileSystemAccess fileSystemAccess;

    private final Step<? super WorkspaceContext, ? extends CachingResult> delegate;

    public AssignImmutableWorkspaceStep(
        FileSystemAccess fileSystemAccess,
        Step<? super WorkspaceContext, ? extends CachingResult> delegate
    ) {
        this.fileSystemAccess = fileSystemAccess;
        this.delegate = delegate;
    }

    @Override
    public WorkspaceResult execute(UnitOfWork work, C context) {
        ImmutableWorkspaceProvider workspaceProvider = ((ImmutableUnitOfWork) work).getWorkspaceProvider();
        String workspacePath = context.getIdentity().getUniqueId();
        ImmutableWorkspace workspace = workspaceProvider.getWorkspace(workspacePath);

        File immutableWorkspace = workspace.getImmutableLocation();
        FileSystemLocationSnapshot workspaceSnapshot = fileSystemAccess.read(immutableWorkspace.getAbsolutePath());
        if (workspaceSnapshot instanceof DirectorySnapshot) {
            // Load origin metadata
            // TODO Validate workspace
            // TODO Make this nicer
            return new WorkspaceResult(CachingResult.shortcutResult(Execution.skipped(UP_TO_DATE, work), null, Duration.ZERO), immutableWorkspace);
        }

        return workspace.withTemporaryWorkspace(temporaryWorkspace -> {
            WorkspaceContext delegateContext = new WorkspaceContext(context, temporaryWorkspace, null, true);
            CachingResult delegateResult = delegate.execute(work, delegateContext);
            if (delegateResult.getExecution().isSuccessful()) {
                fileSystemAccess.write(ImmutableList.of(immutableWorkspace.getAbsolutePath()), () -> {
                    try {
                        Files.move(temporaryWorkspace.toPath(), immutableWorkspace.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Couldn't move temporary workspace to immutable area", e);
                    }
                });
                return new WorkspaceResult(delegateResult, immutableWorkspace);
            } else {
                // TODO Do not try to capture the workspace in case of a failure
                return new WorkspaceResult(delegateResult, null);
            }
        });
    }
}
