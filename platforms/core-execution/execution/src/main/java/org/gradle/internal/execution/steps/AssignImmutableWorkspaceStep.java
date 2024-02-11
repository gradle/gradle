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

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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

        return loadImmutableWorkspaceIfExists(work, workspace.getImmutableLocation())
            .orElseGet(() -> executeInTemporaryWorkspace(work, context, workspace));
    }

    private Optional<WorkspaceResult> loadImmutableWorkspaceIfExists(UnitOfWork work, File immutableLocation) {
        FileSystemLocationSnapshot workspaceSnapshot = fileSystemAccess.read(immutableLocation.getAbsolutePath());
        switch (workspaceSnapshot.getType()) {
            case Directory:
                return Optional.of(loadImmutableWorkspace(work, immutableLocation));
            case RegularFile:
                throw new IllegalStateException(
                    "Immutable workspace is occupied by a file: " + immutableLocation.getAbsolutePath() + ". " +
                        "Deleting the file in question can allow the content to be recreated.");
            case Missing:
                return Optional.empty();
            default:
                throw new AssertionError();
        }
    }

    private WorkspaceResult loadImmutableWorkspace(UnitOfWork work, File immutableLocation) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots = outputSnapshotter.snapshotOutputs(work, immutableLocation);

        // Verify output hashes
        ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(outputSnapshots);
        ImmutableWorkspaceMetadata metadata = workspaceMetadataStore.loadWorkspaceMetadata(immutableLocation);
        if (!metadata.getOutputPropertyHashes().equals(outputHashes)) {
            throw new IllegalStateException(
                "Immutable workspace contents have been modified: " + immutableLocation.getAbsolutePath() + ". " +
                    "These workspace directories are not supposed to be modified once they are created. " +
                    "Deleting the directory in question can allow the content to be recreated.");
        }

        OriginMetadata originMetadata = metadata.getOriginMetadata();
        ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, outputSnapshots, originMetadata, true);
        return new WorkspaceResult(
            CachingResult.shortcutResult(
                Duration.ZERO,
                Execution.skipped(UP_TO_DATE, work),
                afterExecutionOutputState,
                null,
                originMetadata),
            immutableLocation);
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

                return moveTemporaryWorkspaceToImmutableLocation(
                    new WorkspaceMoveHandler(work, workspace, temporaryWorkspace, workspace.getImmutableLocation(), delegateResult));
            } else {
                // TODO Do not capture a null workspace in case of a failure
                return new WorkspaceResult(delegateResult, null);
            }
        });
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

    private WorkspaceResult moveTemporaryWorkspaceToImmutableLocation(WorkspaceMoveHandler move) {
        return move.executeMoveOr(moveFailedException -> {
            // On Windows, files left open by the executed work can legitimately prevent an atomic move of the temporary directory
            // In this case we'll try to make a copy of the temporary workspace to another temporary workspace, and move that to
            // the immutable location and then delete the original temporary workspace (if we can).
            LOGGER.debug("Could not move temporary workspace ({}) to immutable location ({}), attempting copy-then-move",
                move.temporaryWorkspace.getAbsolutePath(), move.immutableLocation.getAbsolutePath(), moveFailedException);
            return move.workspace.withTemporaryWorkspace(secondaryTemporaryWorkspace -> {
                WorkspaceResult result = move
                    .duplicateTemporaryWorkspaceTo(secondaryTemporaryWorkspace)
                    .executeMoveOrThrow();
                move.removeTemporaryWorkspace();
                return result;
            });
        });
    }

    private class WorkspaceMoveHandler {
        private final UnitOfWork work;
        private final ImmutableWorkspace workspace;
        private final File temporaryWorkspace;
        private final File immutableLocation;
        private final CachingResult delegateResult;

        public WorkspaceMoveHandler(UnitOfWork work, ImmutableWorkspace workspace, File temporaryWorkspace, File immutableLocation, CachingResult delegateResult) {
            this.work = work;
            this.workspace = workspace;
            this.temporaryWorkspace = temporaryWorkspace;
            this.immutableLocation = immutableLocation;
            this.delegateResult = delegateResult;
        }

        public WorkspaceResult executeMoveOr(Function<FileSystemException, WorkspaceResult> failedMoveHandler) {
            try {
                fileSystemAccess.moveAtomically(temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath());
                return new WorkspaceResult(delegateResult, immutableLocation);
            } catch (FileSystemException moveWorkspaceException) {
                // `Files.move()` says it would throw DirectoryNotEmptyException, but it's a lie, so this is the best we can catch here
                if (immutableLocation.isDirectory()) {
                    LOGGER.debug("Could not move temporary workspace ({}) to immutable location ({}), assuming it was moved in place concurrently",
                        temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath(), moveWorkspaceException);
                    WorkspaceResult existingImmutableResult = loadImmutableWorkspace(work, immutableLocation);
                    removeTemporaryWorkspace();
                    return existingImmutableResult;
                } else {
                    return failedMoveHandler.apply(moveWorkspaceException);
                }
            } catch (IOException e) {
                throw unableToMoveBecause(e);
            }
        }

        public WorkspaceResult executeMoveOrThrow() {
            return executeMoveOr(moveFailedException -> {
                throw unableToMoveBecause(moveFailedException);
            });
        }

        public WorkspaceMoveHandler duplicateTemporaryWorkspaceTo(File duplicateTemporaryWorkspace) {
            try {
                FileUtils.copyDirectory(temporaryWorkspace, duplicateTemporaryWorkspace, file -> true, true, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException duplicateCopyException) {
                throw new UncheckedIOException(
                    String.format("Could not make copy of temporary workspace (%s) to (%s)",
                        temporaryWorkspace.getAbsolutePath(), duplicateTemporaryWorkspace.getAbsolutePath()), duplicateCopyException);
            }
            return new WorkspaceMoveHandler(work, workspace, duplicateTemporaryWorkspace, immutableLocation, delegateResult);
        }

        private void removeTemporaryWorkspace() {
            try {
                deleter.deleteRecursively(temporaryWorkspace);
            } catch (IOException removeTempException) {
                // On Windows it is possible that workspaces with open files cannot be deleted
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Could not remove temporary workspace: {}", temporaryWorkspace.getAbsolutePath(), removeTempException);
                } else {
                    LOGGER.info("Could not remove temporary workspace: {}: {}", temporaryWorkspace.getAbsolutePath(), removeTempException.getMessage());
                }
            }
        }

        @CheckReturnValue
        private UncheckedIOException unableToMoveBecause(IOException cause) {
            return new UncheckedIOException(String.format("Could not move temporary workspace (%s) to immutable location (%s)",
                temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath()), cause);
        }
    }
}
