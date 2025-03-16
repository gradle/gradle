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
import org.gradle.internal.deprecation.DeprecationLogger;
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
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.Maps.immutableEntry;
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE;
import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE;

/**
 * Assigns an immutable workspace to the work, and makes sure it contains the correct outputs.
 *
 * <ul>
 * <li>If an immutable workspace already exists, it is checked for consistency, and is returned
 * if found correct.</li>
 * <li>If the workspace is inconsistent (the output hashes stored in {code metadata.bin} do not match
 * the hashes taken by snapshotting the current outputs), the workspace is moved to a temporary
 * location and we fall back to re-executing the work.</li>
 * <li>If we end up executing the work (either because there is no existing immutable workspace, or it is
 * inconsistent), then a unique temporary workspace directory is provided to the work to create its outputs
 * in, and the work is executed.</li>
 * <li>When the execution of the work finishes, we snapshot the outputs of the work, and store their hashes
 * in the {code metadata.bin} file in the temporary workspace directory.</li>
 * <li>We then attempt to move the temporary workspace directory (including the newly generated
 * {code metadata.bin} into its permanent immutable location. The move happens atomically, and if
 * successful, the newly created immutable workspace is returned.</li>
 * <li>If the move fails because a directory is already present in the immutable location, we assume
 * that we got into a race condition with another Gradle process, and try to reuse the newly appeared
 * results after a consistency check.</li>
 * <li>If the move fails because of file access permissions, we assume that one of the files in the
 * temporary workspace are still open, preventing the move from happening. In this case we make a
 * defensive copy of the temporary workspace to yet another temporary workspace, and attempt to move
 * the copy into the immutable location instead.</li>
 * </ul>
 */
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

        return loadImmutableWorkspaceIfExists(work, workspace)
            .orElseGet(() -> executeInTemporaryWorkspace(work, context, workspace));
    }

    private Optional<WorkspaceResult> loadImmutableWorkspaceIfExists(UnitOfWork work, ImmutableWorkspace workspace) {
        File immutableLocation = workspace.getImmutableLocation();
        FileSystemLocationSnapshot workspaceSnapshot = fileSystemAccess.read(immutableLocation.getAbsolutePath());
        switch (workspaceSnapshot.getType()) {
            case Directory:
                return loadImmutableWorkspaceIfConsistent(work, workspace);
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

    private Optional<WorkspaceResult> loadImmutableWorkspaceIfConsistent(UnitOfWork work, ImmutableWorkspace workspace) {
        File immutableLocation = workspace.getImmutableLocation();
        ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots = outputSnapshotter.snapshotOutputs(work, immutableLocation);

        // Verify output hashes
        ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(outputSnapshots);
        ImmutableWorkspaceMetadata metadata = workspaceMetadataStore.loadWorkspaceMetadata(immutableLocation);
        if (!metadata.getOutputPropertyHashes().equals(outputHashes)) {
            return workspace.withTemporaryWorkspace(temporaryWorkspace -> {
                moveInconsistentImmutableWorkspaceToTemporaryLocation(immutableLocation, temporaryWorkspace, outputSnapshots);
                return Optional.empty();
            });
        }

        return Optional.of(loadImmutableWorkspace(work, immutableLocation, metadata, outputSnapshots));
    }

    private static WorkspaceResult loadImmutableWorkspace(UnitOfWork work, File immutableLocation, ImmutableWorkspaceMetadata metadata, ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots) {
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

    private void moveInconsistentImmutableWorkspaceToTemporaryLocation(File immutableLocation, File failedWorkspaceLocation, ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots) {
        fileSystemAccess.invalidate(ImmutableList.of(immutableLocation.getAbsolutePath()));
        String outputHashes = outputSnapshots.entrySet().stream()
            .map(entry -> entry.getKey() + ":\n" + entry.getValue().roots()
                .map(AssignImmutableWorkspaceStep::describeSnapshot)
                .collect(Collectors.joining("\n")))
            .collect(Collectors.joining("\n"));
        DeprecationLogger.deprecateBehaviour(String.format("The contents of the immutable workspace '%s' have been modified.", immutableLocation.getAbsolutePath()))
            .withContext("These workspace directories are not supposed to be modified once they are created. " +
                "The modification might have been caused by an external process, or could be the result of disk corruption. " +
                String.format("The inconsistent workspace will be moved to '%s', and will be recreated.", failedWorkspaceLocation.getAbsolutePath()) +
                "\n" +
                outputHashes)
            .willBecomeAnErrorInGradle9()
            .undocumented()
            .nagUser();
        try {
            // We move the inconsistent workspace to a "temporary" location as a way to atomically move it out of the permanent workspace.
            // Deleting it in-place is not an option, as we can't do that atomically.
            // By moving the inconsistent workspace we also preserve it for later inspection.
            Files.move(immutableLocation.toPath(), failedWorkspaceLocation.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not move inconsistent immutable workspace (%s) to temporary location (%s)",
                immutableLocation.getAbsolutePath(), failedWorkspaceLocation.getAbsolutePath()), e);
        }
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

                return moveTemporaryWorkspaceToImmutableLocation(workspace,
                    new WorkspaceMoveHandler(work, workspace, temporaryWorkspace, delegateResult));
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

    private WorkspaceResult moveTemporaryWorkspaceToImmutableLocation(ImmutableWorkspace workspace, WorkspaceMoveHandler move) {
        return move.executeMoveOr(moveFailedException -> {
            // On Windows, files left open by the executed work can legitimately prevent an atomic move of the temporary directory
            // In this case we'll try to make a copy of the temporary workspace to another temporary workspace, and move that to
            // the immutable location and then delete the original temporary workspace (if we can).
            LOGGER.debug("Could not move temporary workspace ({}) to immutable location ({}), attempting copy-then-move",
                move.temporaryWorkspace.getAbsolutePath(), workspace.getImmutableLocation().getAbsolutePath(), moveFailedException);
            return workspace.withTemporaryWorkspace(secondaryTemporaryWorkspace -> {
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
        private final CachingResult delegateResult;

        public WorkspaceMoveHandler(UnitOfWork work, ImmutableWorkspace workspace, File temporaryWorkspace, CachingResult delegateResult) {
            this.work = work;
            this.workspace = workspace;
            this.temporaryWorkspace = temporaryWorkspace;
            this.delegateResult = delegateResult;
        }

        public WorkspaceResult executeMoveOr(Function<FileSystemException, WorkspaceResult> failedMoveHandler) {
            File immutableLocation = workspace.getImmutableLocation();
            try {
                fileSystemAccess.moveAtomically(temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath());
                return new WorkspaceResult(delegateResult, immutableLocation);
            } catch (FileSystemException moveWorkspaceException) {
                // `Files.move()` says it would throw DirectoryNotEmptyException, but it's a lie, so this is the best we can catch here
                if (immutableLocation.isDirectory()) {
                    LOGGER.debug("Could not move temporary workspace ({}) to immutable location ({}), assuming it was moved in place concurrently",
                        temporaryWorkspace.getAbsolutePath(), immutableLocation.getAbsolutePath(), moveWorkspaceException);
                    return loadImmutableWorkspaceIfConsistent(work, workspace)
                        // If we found a consistent workspace, we can use it
                        .map(result -> {
                            removeTemporaryWorkspace();
                            return result;
                        })
                        // Otherwise we should have managed to move the offending workspace out of the way,
                        // so we can retry the move the temporary workspace in once more
                        .orElseGet(this::executeMoveOrThrow);
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
            return new WorkspaceMoveHandler(work, workspace, duplicateTemporaryWorkspace, delegateResult);
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
                temporaryWorkspace.getAbsolutePath(), workspace.getImmutableLocation().getAbsolutePath()), cause);
        }
    }

    private static String describeSnapshot(FileSystemLocationSnapshot root) {
        StringBuilder builder = new StringBuilder();
        root.accept(new FileSystemSnapshotHierarchyVisitor() {
            private int indent = 0;

            @Override
            public void enterDirectory(DirectorySnapshot directorySnapshot) {
                indent++;
            }

            @Override
            public void leaveDirectory(DirectorySnapshot directorySnapshot) {
                indent--;
            }

            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                for (int i = 0; i < indent; i++) {
                    builder.append("  ");
                }
                builder.append(" - ");
                builder.append(snapshot.getName());
                builder.append(" (");
                builder.append(snapshot.getType());
                builder.append(", ");
                builder.append(snapshot.getHash());
                builder.append(")");
                builder.append("\n");
                return CONTINUE;
            }
        });
        return builder.toString();
    }
}
