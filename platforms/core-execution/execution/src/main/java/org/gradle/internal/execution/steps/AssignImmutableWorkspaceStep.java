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
import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.ImmutableUnitOfWork;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata;
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore;
import org.gradle.internal.execution.history.impl.DefaultExecutionOutputState;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ConcurrentResult;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.ImmutableWorkspace;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.Maps.immutableEntry;
import static org.gradle.internal.execution.Execution.ExecutionOutcome.UP_TO_DATE;
import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE;

/**
 * Assigns an immutable workspace to the work, and makes sure it contains the correct outputs.
 *
 * <p>This step also makes sure that only one thread is executing the work at a time.</p>
 *
 * <p>The algorithm to run the work is as follows:
 * <ul>
 * <li>We first check if workspace exists</li>
 * <li>We then check if the workspace is soft-deleted</li>
 * <li>If an immutable workspace already exists and it's not soft-deleted, it is checked for consistency, and is returned
 * if found correct.</li>
 * <li>If the workspace doesn't exist or is soft-deleted or {@code metadata.bin} doesn't exist or is inconsistent (the output hashes stored in {@code metadata.bin} do not match
 * the hashes taken by snapshotting the current outputs), we acquire a file lock to re-execute it.</li>
 * <li>Under file lock we check again if another process already created a workspace and we check it if consistent (double-checked locking) </li>
 * <li>If workspace is still not found we execute work</li>
 * <li>When the execution of the work finishes, we snapshot the outputs of the work, and store their hashes
 * in the {@code metadata.bin} file in the temporary workspace directory.</li>
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
        // Loads a workspace result or creates it,
        // but only execute loadOrCreateWorkspace() once across all threads that try to run it at the same time
        ConcurrentResult<WorkspaceResult> result = workspace.getOrCompute(() -> loadOrCreateWorkspace(work, workspace, context));

        // If a result is produced by the current thread, return it, otherwise map it as up-to-date
        return result.isProducedByCurrentThread()
            ? result.get()
            : mapConcurrentResultToUpToDate(result.get(), work, workspace);
    }

    private static WorkspaceResult mapConcurrentResultToUpToDate(WorkspaceResult result, UnitOfWork work, ImmutableWorkspace workspace) {
        if (result.getExecution().isSuccessful()) {
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            ExecutionOutputState executionOutputState = result.getAfterExecutionOutputState().get();
            return getUpToDate(work, workspace.getImmutableLocation(), executionOutputState.getOutputFilesProducedByWork(), executionOutputState.getOriginMetadata());
        } else {
            return new WorkspaceResult(result, null);
        }
    }

    private WorkspaceResult loadOrCreateWorkspace(UnitOfWork work, ImmutableWorkspace workspace, C context) {
        // First try to load a workspace and if it's already there and complete return the result
        WorkspaceLoad initialLoad = loadImmutableWorkspaceIfExists(work, workspace);
        if (initialLoad.isSuccess()) {
            return initialLoad.getResult();
        }

        // If the workspace doesn't exist or is soft-deleted or broken, execute the work with a file lock
        return workspace.withFileLock(() -> {
            // We need to invalidate snapshots in case another process populated the workspace just before us
            Runnable invalidateSnapshots = () -> fileSystemAccess.invalidate(ImmutableList.of(workspace.getImmutableLocation().getAbsolutePath()));
            // First, try to load it again if another process populated it just before us (double-checked locking)
            WorkspaceLoad load = loadImmutableWorkspaceIfComplete(work, workspace, invalidateSnapshots);

            WorkspaceResult result = load.isSuccess()
                ? load.getResult()
                : executeInWorkspace(work, context, workspace);

            // Only un soft-delete if soft-deleted at initial load,
            // if an entry was soft-deleted while acquiring lock, we leave it to the next read to handle it
            if (initialLoad.isSoftDeleted()) {
                workspace.ensureUnSoftDeleted();
            }
            return result;
        });
    }

    private WorkspaceLoad loadImmutableWorkspaceIfExists(UnitOfWork work, ImmutableWorkspace workspace) {
        File immutableLocation = workspace.getImmutableLocation();
        FileSystemLocationSnapshot snapshot = fileSystemAccess.read(immutableLocation.getAbsolutePath());
        switch (snapshot.getType()) {
            case Directory:
                if (workspace.isSoftDeleted()) {
                    // If the workspace is soft deleted, we need to load it with file lock, since hard delete operation could run.
                    return WorkspaceLoad.softDeleted();
                }
                // Don't invalidate snapshots here, we have just read them
                return loadImmutableWorkspaceIfComplete(work, workspace, () -> {});
            case RegularFile:
                throw new IllegalStateException(
                    "Immutable workspace is occupied by a file: " + immutableLocation.getAbsolutePath() + ". " +
                        "Deleting the file in question can allow the content to be recreated.");
            case Missing:
                return WorkspaceLoad.missingOrBroken();
            default:
                throw new AssertionError();
        }
    }

    private WorkspaceLoad loadImmutableWorkspaceIfComplete(UnitOfWork work, ImmutableWorkspace workspace, Runnable snapshotsInvalidation) {
        File immutableLocation = workspace.getImmutableLocation();
        Optional<ImmutableWorkspaceMetadata> metadata = workspaceMetadataStore.loadWorkspaceMetadata(immutableLocation);

        if (!metadata.isPresent()) {
            return WorkspaceLoad.missingOrBroken();
        }

        // Verify output hashes
        snapshotsInvalidation.run();
        ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots = outputSnapshotter.snapshotOutputs(work, immutableLocation);
        ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(outputSnapshots);
        if (!metadata.get().getOutputPropertyHashes().equals(outputHashes)) {
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
            return WorkspaceLoad.missingOrBroken();
        }

        return WorkspaceLoad.success(getUpToDate(work, workspace.getImmutableLocation(), outputSnapshots, metadata.get().getOriginMetadata()));
    }

    private static WorkspaceResult getUpToDate(UnitOfWork work, File workspace, ImmutableSortedMap<String, FileSystemSnapshot> outputFilesProducedByWork, OriginMetadata originMetadata) {
        ExecutionOutputState afterExecutionOutputState = new DefaultExecutionOutputState(true, outputFilesProducedByWork, originMetadata, true);
        CachingResult cachingResult = CachingResult.shortcutResult(
            Duration.ZERO,
            Execution.skipped(UP_TO_DATE, work),
            afterExecutionOutputState,
            null,
            originMetadata
        );
        return new WorkspaceResult(cachingResult, workspace);
    }

    private WorkspaceResult executeInWorkspace(UnitOfWork work, C context, ImmutableWorkspace workspace) {
        File workspaceDir = workspace.getImmutableLocation();
        WorkspaceContext workspaceContext = new WorkspaceContext(context, workspaceDir);

        // There is no previous execution in the immutable case
        PreviousExecutionContext previousExecutionContext = new PreviousExecutionContext(workspaceContext, null);

        // Ensure an empty directory in case of stale files
        ensureEmptyDirectory(workspaceDir);
        // Invalidate snapshots since we cleaned the directory
        fileSystemAccess.invalidate(ImmutableList.of(workspaceDir.getAbsolutePath()));
        // Execute
        CachingResult delegateResult = delegate.execute(work, previousExecutionContext);

        if (delegateResult.getExecution().isSuccessful()) {
            // Store workspace metadata
            // TODO Capture in the type system the fact that we always have an after-execution output state here
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            ExecutionOutputState executionOutputState = delegateResult.getAfterExecutionOutputState().get();
            ImmutableListMultimap<String, HashCode> outputHashes = calculateOutputHashes(executionOutputState.getOutputFilesProducedByWork());
            ImmutableWorkspaceMetadata metadata = new ImmutableWorkspaceMetadata(executionOutputState.getOriginMetadata(), outputHashes);
            workspaceMetadataStore.storeWorkspaceMetadata(workspaceDir, metadata);

            return new WorkspaceResult(delegateResult, workspaceDir);
        } else {
            // TODO Do not capture a null workspace in case of a failure
            return new WorkspaceResult(delegateResult, null);
        }
    }

    private void ensureEmptyDirectory(File workspace) {
        try {
            deleter.ensureEmptyDirectory(workspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private static class WorkspaceLoad {
        enum Status {
            SUCCESS,
            SOFT_DELETED,
            MISSING_OR_BROKEN
        }

        @Nullable
        private final WorkspaceResult workspaceResult;
        private final Status status;

        private WorkspaceLoad(@Nullable WorkspaceResult workspaceResult, Status status) {
            this.workspaceResult = workspaceResult;
            this.status = status;
        }

        public WorkspaceResult getResult() {
            return checkNotNull(workspaceResult);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isSoftDeleted() {
            return status == Status.SOFT_DELETED;
        }

        public static WorkspaceLoad success(WorkspaceResult workspaceResult) {
            return new WorkspaceLoad(workspaceResult, Status.SUCCESS);
        }

        public static WorkspaceLoad softDeleted() {
            return new WorkspaceLoad(null, Status.SOFT_DELETED);
        }

        public static WorkspaceLoad missingOrBroken() {
            return new WorkspaceLoad(null, Status.MISSING_OR_BROKEN);
        }
    }
}
