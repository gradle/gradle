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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.FileUtils;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.Maps.immutableEntry;
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;

public class AssignImmutableWorkspaceStep<C extends IdentityContext> implements Step<C, WorkspaceResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssignImmutableWorkspaceStep.class);

    private final Deleter deleter;
    private final FileSystemAccess fileSystemAccess;

    private final ImmutableWorkspaceMetadataStore workspaceMetadataStore;
    private final OutputSnapshotter outputSnapshotter;
    private final Step<? super PreviousExecutionContext, ? extends CachingResult> delegate;

    public AssignImmutableWorkspaceStep(
        Deleter deleter,
        FileSystemAccess fileSystemAccess,
        ImmutableWorkspaceMetadataStore workspaceMetadataStore,
        OutputSnapshotter outputSnapshotter,
        Step<? super PreviousExecutionContext, ? extends CachingResult> delegate
    ) {
        this.deleter = deleter;
        this.fileSystemAccess = fileSystemAccess;
        this.workspaceMetadataStore = workspaceMetadataStore;
        this.outputSnapshotter = outputSnapshotter;
        this.delegate = delegate;
    }

    @Override
    public WorkspaceResult execute(UnitOfWork work, C context) {
        ImmutableWorkspaceProvider workspaceProvider = ((ImmutableUnitOfWork) work).getWorkspaceProvider();
        String uniqueId = context.getIdentity().getUniqueId();
        ImmutableWorkspace workspace = workspaceProvider.getWorkspace(uniqueId);

        return returnImmutableWorkspaceOr(work, workspace.getImmutableLocation(),
            () -> executeInTemporaryWorkspace(work, context, workspace));
    }

    private WorkspaceResult returnImmutableWorkspaceOr(UnitOfWork work, File immutableLocation, Supplier<WorkspaceResult> missingImmutableWorkspaceAction) {
        FileSystemLocationSnapshot workspaceSnapshot = fileSystemAccess.read(immutableLocation.getAbsolutePath());
        switch (workspaceSnapshot.getType()) {
            case Directory:
                return returnUpToDateImmutableWorkspace(work, immutableLocation);
            case RegularFile:
                throw new IllegalStateException("Immutable workspace is occupied by a file: " + immutableLocation.getAbsolutePath());
            case Missing:
                return missingImmutableWorkspaceAction.get();
            default:
                throw new AssertionError();
        }
    }

    private WorkspaceResult returnUpToDateImmutableWorkspace(UnitOfWork work, File immutableWorkspace) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots = outputSnapshotter.snapshotOutputs(work, immutableWorkspace);

        // Verify output hashes
        ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(outputSnapshots);
        ImmutableWorkspaceMetadata metadata = workspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace);
        if (!metadata.getOutputPropertyHashes().equals(outputHashes)) {
            throw new IllegalStateException("Workspace has been changed: " + immutableWorkspace.getAbsolutePath());
        }

        OriginMetadata originMetadata = metadata.getOriginMetadata();
        ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, outputSnapshots, originMetadata, true);
        return new WorkspaceResult(CachingResult.shortcutResult(Duration.ZERO, Execution.skipped(UP_TO_DATE, work), afterExecutionOutputState, null, originMetadata), immutableWorkspace);
    }

    private WorkspaceResult executeInTemporaryWorkspace(UnitOfWork work, C context, ImmutableWorkspace workspace) {
        return workspace.withTemporaryWorkspace(temporaryWorkspace -> {
            WorkspaceContext workspaceContext = new WorkspaceContext(context, temporaryWorkspace, null, true);

            // We don't need to invalidate the temporary workspace, as there is surely nothing there yet,
            // but we still want to record that this build is writing to the given location, so that
            // file system watching won't care about it
            fileSystemAccess.invalidate(ImmutableList.of(temporaryWorkspace.getAbsolutePath()));

            // There is no previous execution in the immutable case
            PreviousExecutionContext previousExecutionContext = new PreviousExecutionContext(workspaceContext, null);
            CachingResult delegateResult = delegate.execute(work, previousExecutionContext);

            if (delegateResult.getExecution().isSuccessful()) {
                // Store workspace metadata
                // TODO Capture in the type system the fact that we always have an after-execution output state here
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                ExecutionOutputState executionOutputState = delegateResult.getAfterExecutionOutputState().get();
                ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(executionOutputState.getOutputFilesProducedByWork());
                ImmutableWorkspaceMetadata metadata = new ImmutableWorkspaceMetadata(executionOutputState.getOriginMetadata(), outputHashes);
                workspaceMetadataStore.storeWorkspaceMetadata(temporaryWorkspace, metadata);

                File immutableLocation = workspace.getImmutableLocation();
                try {
                    fileSystemAccess.moveAtomically(temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath());
                } catch (FileSystemException moveWorkspaceException) {
                    // `Files.move()` says it would throw DirectoryNotEmptyException, but it's a lie, so this is the best we can catch here
                    if (immutableLocation.isDirectory()) {
                        LOGGER.debug("Could not move temporary worksapce ({}) to immutable location ({}), assuming it was moved in place concurrently",
                            temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath(), moveWorkspaceException);
                        return returnExistingImmutableWorkspaceAndDropTemporaryWorkspace(work, temporaryWorkspace, immutableLocation);
                    } else {
                        // On Windows files left open by the work can legitimately prevent an atomic move of the temporary directory
                        // In this case we'll try to make a copy of the temporary workspace to another temporary workspace, and move that to the immutable location
                        // and then delete the original temporary workspace (if we can).
                        LOGGER.debug("Could not move temporary worksapce ({}) to immutable location ({}), attempting copy-then-move",
                            temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath(), moveWorkspaceException);
                        attemptToMoveTemporaryWorkspaceByDuplicatingItFirst(workspace, temporaryWorkspace, immutableLocation);
                    }
                } catch (IOException e) {
                    throw unableToMove(temporaryWorkspace, immutableLocation, e);
                }
                return new WorkspaceResult(delegateResult, immutableLocation);
            } else {
                // TODO Do not try to capture the workspace in case of a failure
                return new WorkspaceResult(delegateResult, null);
            }
        });
    }

    private WorkspaceResult returnExistingImmutableWorkspaceAndDropTemporaryWorkspace(UnitOfWork work, File temporaryWorkspace, File immutableLocation) {
        WorkspaceResult existingImmutableResult = returnImmutableWorkspaceOr(work, immutableLocation,
            () -> {
                throw unableToMove(temporaryWorkspace, immutableLocation, new IOException("Immutable workspace gone missing"));
            });
        removeTemporaryWorkspace(temporaryWorkspace);
        return existingImmutableResult;
    }

    private void attemptToMoveTemporaryWorkspaceByDuplicatingItFirst(ImmutableWorkspace workspace, File temporaryWorkspace, File immutableLocation) {
        workspace.withTemporaryWorkspace(duplicateTemporaryWorkspace -> {
            try {
                FileUtils.copyDirectory(temporaryWorkspace, duplicateTemporaryWorkspace, file -> true, true, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException duplicateTemporaryWorkspaceException) {
                throw new UncheckedIOException(
                    String.format("Could not make copy of temporary workspace (%s) to (%s)",
                        temporaryWorkspace.getAbsolutePath(), duplicateTemporaryWorkspace.getAbsolutePath()),
                    duplicateTemporaryWorkspaceException);
            }
            try {
                fileSystemAccess.moveAtomically(duplicateTemporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath());
            } catch (IOException e) {
                throw unableToMove(duplicateTemporaryWorkspace, immutableLocation, e);
            }
            removeTemporaryWorkspace(temporaryWorkspace);
            return immutableLocation;
        });
    }

    private void removeTemporaryWorkspace(File temporaryWorkspace) {
        try {
            deleter.deleteRecursively(temporaryWorkspace);
        } catch (IOException removeTempException) {
            // On Windows it is possible that workspaces with open files cannot be deleted
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not remove temporary workspace: {}", temporaryWorkspace.getAbsolutePath(), removeTempException);
            } else {
                LOGGER.warn("Could not remove temporary workspace: {}: {}", temporaryWorkspace.getAbsolutePath(), removeTempException.getMessage());
            }
        }
    }

    private static UncheckedIOException unableToMove(File temporaryWorkspace, File immutableWorkspace, IOException cause) {
        throw new UncheckedIOException(String.format("Could not move temporary workspace (%s) to immutable location (%s)",
            temporaryWorkspace.getAbsolutePath(), immutableWorkspace.getAbsolutePath()), cause);
    }

    private static ImmutableListMultimap<String, HashCode> calculateOutputHashes(ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots) {
        return outputSnapshots.entrySet().stream()
            .flatMap(entry ->
                entry.getValue().roots()
                    .map(locationSnapshot -> immutableEntry(entry.getKey(), locationSnapshot.getHash())))
            .collect(toImmutableListMultimap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }
}
