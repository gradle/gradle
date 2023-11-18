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
import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.input.BufferedFileChannelInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;

public class AssignImmutableWorkspaceStep<C extends IdentityContext> implements Step<C, WorkspaceResult> {
    private final FileSystemAccess fileSystemAccess;

    private final OriginMetadataFactory originMetadataFactory;
    private final OutputSnapshotter outputSnapshotter;
    private final Step<? super WorkspaceContext, ? extends CachingResult> delegate;

    public AssignImmutableWorkspaceStep(
        FileSystemAccess fileSystemAccess,
        OriginMetadataFactory originMetadataFactory,
        OutputSnapshotter outputSnapshotter,
        Step<? super WorkspaceContext, ? extends CachingResult> delegate
    ) {
        this.fileSystemAccess = fileSystemAccess;
        this.originMetadataFactory = originMetadataFactory;
        this.outputSnapshotter = outputSnapshotter;
        this.delegate = delegate;
    }

    @Override
    public WorkspaceResult execute(UnitOfWork work, C context) {
        ImmutableWorkspaceProvider workspaceProvider = ((ImmutableUnitOfWork) work).getWorkspaceProvider();
        ImmutableWorkspace workspace = workspaceProvider.getWorkspace(context.getIdentity().getUniqueId());

        File immutableWorkspace = workspace.getImmutableLocation();
        FileSystemLocationSnapshot workspaceSnapshot = fileSystemAccess.read(immutableWorkspace.getAbsolutePath());
        switch (workspaceSnapshot.getType()) {
            case Directory:
                return returnUpToDateImmutableWorkspace(work, immutableWorkspace);
            case RegularFile:
                throw new IllegalStateException("Immutable workspace is occupied by a file: " + immutableWorkspace.getAbsolutePath());
            case Missing:
                return executeInTemporaryWorkspace(work, context, workspace);
            default:
                throw new AssertionError();
        }
    }

    private WorkspaceResult returnUpToDateImmutableWorkspace(UnitOfWork work, File immutableWorkspace) {
        // TODO Validate workspace
        OriginMetadata originMetadata = loadOriginMetadata(immutableWorkspace);
        ImmutableSortedMap<String, FileSystemSnapshot> outputFiles = outputSnapshotter.snapshotOutputs(work, immutableWorkspace);
        ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, outputFiles, originMetadata, true);
        return new WorkspaceResult(CachingResult.shortcutResult(Duration.ZERO, Execution.skipped(UP_TO_DATE, work), afterExecutionOutputState, null, originMetadata), immutableWorkspace);
    }

    private OriginMetadata loadOriginMetadata(File immutableWorkspace) {
        File originFile = getOriginFile(immutableWorkspace);
        try (InputStream originInput = new BufferedFileChannelInputStream(originFile)) {
            return originMetadataFactory.createReader().execute(originInput);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read origin metadata from " + originFile, e);
        }
    }

    private WorkspaceResult executeInTemporaryWorkspace(UnitOfWork work, C context, ImmutableWorkspace workspace) {
        return workspace.withTemporaryWorkspace(temporaryWorkspace -> {
            WorkspaceContext delegateContext = new WorkspaceContext(context, temporaryWorkspace, null, true);
            // We don't need to invalidate the temporary workspace, as there is surely nothing there yet,
            // but we still want to record that this build is writing to the given location, so that
            // file system watching won't care about it
            fileSystemAccess.write(ImmutableList.of(temporaryWorkspace.getAbsolutePath()), () -> {});
            CachingResult delegateResult = delegate.execute(work, delegateContext);
            if (delegateResult.getExecution().isSuccessful()) {
                storeOriginMetadata(work, temporaryWorkspace, context.getIdentity().getUniqueId(), delegateResult);
                // TODO Store output hashes for validation when the workspace is returned as up-to-date
                // TODO Handle if move failed because there's something there already;
                //      it's probably a race condition, and we should return the now existing immutable directory
                //      and remove the temporary one
                File immutableLocation = workspace.getImmutableLocation();
                fileSystemAccess.moveAtomically(temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath());
                return new WorkspaceResult(delegateResult, immutableLocation);
            } else {
                // TODO Do not try to capture the workspace in case of a failure
                return new WorkspaceResult(delegateResult, null);
            }
        });
    }

    private void storeOriginMetadata(UnitOfWork work, File temporaryWorkspace, String identity, Result delegateResult) {
        File originFile = getOriginFile(temporaryWorkspace);
        try {
            UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream(4096);
            originMetadataFactory.createWriter(identity, work.getClass(), delegateResult.getDuration())
                .execute(data);
            try (OutputStream outputStream = Files.newOutputStream(originFile.toPath())) {
                data.writeTo(outputStream);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write origin metadata to " + originFile, e);
        }
    }

    private static File getOriginFile(File immutableWorkspace) {
        return new File(immutableWorkspace, "origin.bin");
    }
}
