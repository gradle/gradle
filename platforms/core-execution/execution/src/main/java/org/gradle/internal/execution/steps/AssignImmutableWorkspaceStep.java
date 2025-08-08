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
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.LockingImmutableWorkspace;
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

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
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

        LockingImmutableWorkspace workspace = workspaceProvider.getLockingWorkspace(uniqueId);
        return loadImmutableWorkspaceIfNotStale(work, workspace)
            .orElseGet(() ->
                workspace.withWorkspaceLock(() -> {
                    fileSystemAccess.invalidate(ImmutableList.of(workspace.getImmutableLocation().getAbsolutePath()));
                    return loadImmutableWorkspaceIfExists(work, workspace)
                        .map(result -> {
                            // If we got result make sure to unstale in case it was stale
                            workspace.unstale();
                            return result;
                        }).orElseGet(() -> {
                            if (workspace.deleteStaleFiles()) {
                                fileSystemAccess.invalidate(ImmutableList.of(workspace.getImmutableLocation().getAbsolutePath()));
                            }
                            return executeInWorkspace(work, context, workspace.getImmutableLocation());
                        });
                }));
    }

    private Optional<WorkspaceResult> loadImmutableWorkspaceIfNotStale(UnitOfWork work, LockingImmutableWorkspace workspace) {
        if (workspace.isStale()) {
            // If the workspace is stale, we need to run the work under the lock
            return Optional.empty();
        }
        return loadImmutableWorkspaceIfExists(work, workspace);
    }


    private Optional<WorkspaceResult> loadImmutableWorkspaceIfExists(UnitOfWork work, ImmutableWorkspace workspace) {
        File immutableLocation = workspace.getImmutableLocation();
        FileSystemLocationSnapshot snapshot = fileSystemAccess.read(immutableLocation.getAbsolutePath());
        switch (snapshot.getType()) {
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
        Optional<ImmutableWorkspaceMetadata> metadata = workspaceMetadataStore.loadWorkspaceMetadata(immutableLocation);

        if (!metadata.isPresent()) {
            return Optional.empty();
        }

        // Verify output hashes
        ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots = outputSnapshotter.snapshotOutputs(work, immutableLocation);
        ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(outputSnapshots);
        if (!metadata.get().getOutputPropertyHashes().equals(outputHashes)) {
            fileSystemAccess.invalidate(ImmutableList.of(immutableLocation.getAbsolutePath()));
            String actualOutputHashes = outputSnapshots.entrySet().stream()
                .map(entry -> entry.getKey() + ":\n" + entry.getValue().roots()
                    .map(AssignImmutableWorkspaceStep::describeSnapshot)
                    .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"));
            LOGGER.warn("The contents of the immutable workspace '{}' have been modified. " +
                "These workspace directories are not supposed to be modified once they are created. " +
                "The modification might have been caused by an external process, or could be the result of disk corruption.\n" +
                "{}", immutableLocation.getAbsolutePath(), actualOutputHashes);
            // Inconsistent workspace, we need to re-execute the work
            return Optional.empty();
        }

        return Optional.of(loadImmutableWorkspace(work, immutableLocation, metadata.get(), outputSnapshots));
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

    private WorkspaceResult executeInWorkspace(UnitOfWork work, C context, File workspace) {
        WorkspaceContext workspaceContext = new WorkspaceContext(context, workspace, null, true);

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
            workspaceMetadataStore.storeWorkspaceMetadata(workspace, metadata);

            return new WorkspaceResult(delegateResult, workspace);
        } else {
            // TODO Do not capture a null workspace in case of a failure
            return new WorkspaceResult(delegateResult, null);
        }
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
