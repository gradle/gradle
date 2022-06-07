/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.transaction;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * A helper class to handle incremental compilation after failure: it makes moving files around easier and reverting state easier.
 */
public class CompileTransaction {

    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final File tempDir;
    private final List<TransactionalDirectory> transactionalDirectories = new ArrayList<>();
    private final AtomicInteger uniqueDirIdGenerator = new AtomicInteger();
    private Predicate<Throwable> stashThrowablePredicate = t -> true;

    private CompileTransaction(File tempDir, FileOperations fileOperations, Deleter deleter) {
        this.tempDir = tempDir;
        this.fileOperations = fileOperations;
        this.deleter = deleter;
    }

    /**
     * Creates a new transactional directory in a temp directory that can be used to move files to.
     * Actual directory is created only when execute() method is called.
     */
    public CompileTransaction newTransactionalDirectory(Consumer<TransactionalDirectory> directoryConsumer) {
        File directory = new File(tempDir, "dir-uniqueId" + uniqueDirIdGenerator.getAndIncrement());
        TransactionalDirectory transactionalDirectory = new TransactionalDirectory(directory, fileOperations, deleter);
        directoryConsumer.accept(transactionalDirectory);
        transactionalDirectories.add(transactionalDirectory);
        return this;
    }

    /**
     * Creates and registers transactional directory only if condition is met. If it is not met directory will not be register. Also, any action set on directory won't run.
     */
    public CompileTransaction newTransactionalDirectory(boolean registerCondition, Consumer<TransactionalDirectory> directoryConsumer) {
        return registerCondition ? newTransactionalDirectory(directoryConsumer) : this;
    }

    public CompileTransaction onFailureRollbackStashIfException(Predicate<Throwable> predicate) {
        stashThrowablePredicate = checkNotNull(predicate);
        return this;
    }

    /**
     * Executes the function that is wrapped in the transaction. Function accepts a work result,
     * that has a result of a stash operation. If some files were stashed, then work will be marked as "did work".
     *
     * Execute will always clean temp directory, so it is empty before execution.
     */
    public <T> T execute(Function<WorkResult, T> function) {
        ensureEmptyDirectoriesBeforeAll();
        WorkResult workResult = stashFilesBeforeExecution();
        try {
            transactionalDirectories.forEach(dir -> dir.beforeExecutionAction.run());
            T result = function.apply(workResult);
            transactionalDirectories.forEach(dir -> dir.onSuccessMoveAction.run());
            return result;
        } catch (Throwable t) {
            if (stashThrowablePredicate.test(t)) {
                transactionalDirectories.forEach(dir -> dir.stashRollbackAction.run());
            }
            throw t;
        } finally {
            transactionalDirectories.forEach(dir -> dir.afterExecutionAlwaysDoAction.run());
        }
    }

    private WorkResult stashFilesBeforeExecution() {
        boolean didWork = false;
        for (TransactionalDirectory directory : transactionalDirectories) {
            didWork |= directory.stashFilesAction.getAsBoolean();
        }
        return WorkResults.didWork(didWork);
    }

    private void ensureEmptyDirectoriesBeforeAll() {
        try {
            deleter.ensureEmptyDirectory(tempDir);
            for (TransactionalDirectory directory : transactionalDirectories) {
                if (directory.isCreateDirectoryBeforeExecution) {
                    deleter.ensureEmptyDirectory(directory.getAsFile());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates new transaction.
     *
     * @param tempDir a temporary directory that will be used the create transactional directories, it will always be cleaned before transaction execution.
     * @param fileOperations file operations for move operations.
     * @param deleter for delete operations.
     */
    public static CompileTransaction newTransaction(File tempDir, FileOperations fileOperations, Deleter deleter) {
        return new CompileTransaction(tempDir, fileOperations, deleter);
    }

    public static class TransactionalDirectory {

        private final String uniqueDirectoryId;
        private final Deleter deleter;
        private final FileOperations fileOperations;
        private File directory;
        private BooleanSupplier stashFilesAction = () -> false;
        private Runnable stashRollbackAction = () -> {};
        private Runnable onSuccessMoveAction = () -> {};
        private Runnable beforeExecutionAction = () -> {};
        private Runnable afterExecutionAlwaysDoAction = () -> {};
        private boolean isCreateDirectoryBeforeExecution;

        public TransactionalDirectory(File directory, FileOperations fileOperations, Deleter deleter) {
            this.uniqueDirectoryId = directory.getName();
            this.directory = directory;
            this.fileOperations = fileOperations;
            this.deleter = deleter;
        }

        /**
         * Stash a pattern set of files from source directory to this directory. Files are moved before execution and on failure rolled back.
         */
        public TransactionalDirectory stashFilesForRollbackOnFailure(PatternSet patternSet, File sourceDirectory) {
            if (patternSet == null || patternSet.isEmpty() || sourceDirectory == null || !sourceDirectory.exists()) {
                return this;
            }

            createDirectoryBeforeExecution();
            if (!hasNamePrefix()) {
                withNamePrefix("stash-for-" + sourceDirectory.getName());
            }

            // Create stash function first
            Set<File> newFiles = new HashSet<>();
            stashFilesAction = () -> {
                Set<File> files = fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles();
                moveFilesFromDirectoryTo(files, sourceDirectory, directory, newFiles::add);
                return !newFiles.isEmpty();
            };

            // Then create also unstash function
            stashRollbackAction = () -> moveFilesFromDirectoryTo(newFiles, directory, sourceDirectory, file -> {});
            return this;
        }

        /**
         * Moves files from this directory to destination directory, but only on success. Any file that already exists in destination directory is replaced.
         * Folder hierarchy is automatically created.
         */
        public TransactionalDirectory onSuccessMoveFilesTo(File destinationDirectory) {
            if (destinationDirectory == null) {
                return this;
            }

            createDirectoryBeforeExecution();
            if (!hasNamePrefix()) {
                withNamePrefix("out-for-" + destinationDirectory.getName());
            }

            onSuccessMoveAction = () -> moveAllFilesFromDirectoryTo(directory, destinationDirectory);
            return this;
        }

        /**
         * Sets an action that will be run just before execution.
         */
        public TransactionalDirectory beforeExecutionDo(Runnable runnable) {
            beforeExecutionAction = runnable;
            return this;
        }

        /**
         * Sets an action that will be run after execution always, even if there is a failure.
         */
        public TransactionalDirectory afterExecutionAlwaysDo(Runnable runnable) {
            afterExecutionAlwaysDoAction = runnable;
            return this;
        }

        /**
         * Add additional prefix to the folder. This is optional, but it makes it easier to debug.
         * If stash or move files operation are used with valid directories, prefix is automatically set to 'stash-of-${dir.name}' or 'out-of-${dir.name} if not set manually.
         */
        public TransactionalDirectory withNamePrefix(String namePrefix) {
            this.directory = new File(directory.getParentFile(), namePrefix + "-" + uniqueDirectoryId);
            return this;
        }

        private boolean hasNamePrefix() {
            return !directory.getName().equals(uniqueDirectoryId);
        }

        /**
         * A flag that tells that a directory on disk should be created before execution.
         * If stash or move files operation are used with valid directories, this is set to true by default.
         */
        public TransactionalDirectory createDirectoryBeforeExecution() {
            this.isCreateDirectoryBeforeExecution = true;
            return this;
        }

        public File getAsFile() {
            return directory;
        }

        private void moveFilesFromDirectoryTo(Set<File> files, File sourceDirectory, File destinationDirectory, Consumer<File> newFileCollector) {
            if (files.isEmpty() || sourceDirectory == null) {
                return;
            }
            StaleOutputCleaner.cleanOutputs(deleter.withDeleteStrategy(file -> {
                Path relativePath = sourceDirectory.toPath().relativize(file.toPath());
                File newFile = new File(destinationDirectory, relativePath.toString());
                if (moveFile(file, newFile)) {
                    newFileCollector.accept(newFile);
                    return true;
                }
                return false;
            }), files, sourceDirectory);
        }

        private void moveAllFilesFromDirectoryTo(File sourceDirectory, File destinationDirectory) {
            fileOperations.fileTree(sourceDirectory).visit(fileVisitDetails -> {
                if (!fileVisitDetails.isDirectory()) {
                    File newFile = new File(destinationDirectory, fileVisitDetails.getPath());
                    moveFile(fileVisitDetails.getFile(), newFile);
                }
            });
        }

        /**
         * Moves file to the destination file. It also creates all intermediate folders if they don't exist.
         *
         * Note: If the destination file already exists it replaced.
         */
        private boolean moveFile(File sourceFile, File destinationFile) {
            try {
                destinationFile.getParentFile().mkdirs();
                Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
