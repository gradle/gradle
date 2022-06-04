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
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A helper class to handle incremental compilation after failure: it makes moving files around easier and reverting state easier.
 */
public class CompileTransaction {

    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final Set<File> directoriesToCleanBeforeAll = new HashSet<>();
    private final List<BooleanSupplier> stashBeforeExecutionActions = new ArrayList<>();
    private final List<Runnable> stashAfterExecutionActions = new ArrayList<>();
    private final List<Runnable> onSuccessActions = new ArrayList<>();
    private final List<Runnable> beforeExecutionDoActions = new ArrayList<>();
    private final List<Runnable> afterExecutionAlwaysDoActions = new ArrayList<>();
    private final AtomicInteger uniqueStashDirIdGenerator = new AtomicInteger();
    private Predicate<Throwable> stashThrowablePredicate = t -> true;

    private CompileTransaction(FileOperations fileOperations, Deleter deleter) {
        this.fileOperations = fileOperations;
        this.deleter = deleter;
    }

    /**
     * Makes sure that the given target is an empty directory or if directory doesn't exist it creates it and also all parent directories.
     *
     * For implementation details check {@link org.gradle.internal.file.Deleter#ensureEmptyDirectory(File)}.
     */
    public CompileTransaction beforeAllEnsureEmptyDirectories(File... directories) {
        directoriesToCleanBeforeAll.addAll(Arrays.asList(directories));
        return this;
    }

    /**
     * Stash a pattern set of files from source directory to some stash directory in stashRootDirectory. Files are moved before execution and on failure rolled back.
     */
    public CompileTransaction stashStaleFilesTo(PatternSet patternSet, File sourceDirectory, File stashRootDirectory) {
        if (patternSet == null || patternSet.isEmpty() || sourceDirectory == null) {
            return this;
        }
        // Create stash function first
        AtomicReference<Set<File>> stashedFiles = new AtomicReference<>();
        File stashDirectory = new File(stashRootDirectory, "stash-" + uniqueStashDirIdGenerator.getAndIncrement());
        stashBeforeExecutionActions.add(() -> {
            Set<File> files = fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles();
            stashedFiles.set(moveFilesFromDirectoryTo(files, sourceDirectory, stashDirectory));
            return !stashedFiles.get().isEmpty();
        });

        // Then create also unstash function
        stashAfterExecutionActions.add(() -> moveFilesFromDirectoryTo(stashedFiles.get(), stashDirectory, sourceDirectory));
        return this;
    }

    private Set<File> moveFilesFromDirectoryTo(Set<File> files, File sourceDirectory, File destinationDirectory) {
        if (files.isEmpty() || sourceDirectory == null) {
            return Collections.emptySet();
        }
        Set<File> newFiles = new HashSet<>();
        StaleOutputCleaner.cleanOutputs(deleter.withDeleteStrategy(file -> {
            Path relativePath = sourceDirectory.toPath().relativize(file.toPath());
            File newFile = new File(destinationDirectory, relativePath.toString());
            if (FileUtils.moveFile(file, newFile)) {
                newFiles.add(newFile);
                return true;
            }
            return false;
        }), files, sourceDirectory);
        return newFiles;
    }

    /**
     * Moves files from source directory to destination directory, but only on success. Any file that already exist in destination directory is replaced.
     * Folder hierarchy is automatically created.
     */
    public CompileTransaction onSuccessMoveAllFilesFromDirectoryTo(File sourceDirectory, File destinationDirectory) {
        onSuccessActions.add(() -> moveAllFilesFromDirectoryTo(sourceDirectory, destinationDirectory));
        return this;
    }

    private void moveAllFilesFromDirectoryTo(File sourceDirectory, File destinationDirectory) {
        fileOperations.fileTree(sourceDirectory).visit(fileVisitDetails -> {
            if (!fileVisitDetails.isDirectory()) {
                File newFile = new File(destinationDirectory, fileVisitDetails.getPath());
                FileUtils.moveFile(fileVisitDetails.getFile(), newFile);
            }
        });
    }

    /**
     * Adds a predicate to test if transaction should roll back stash or not.
     * By default, any exception will cause rollback.
     */
    public CompileTransaction onFailureRollbackStashIfException(Predicate<Throwable> predicate) {
        stashThrowablePredicate = checkNotNull(predicate);
        return this;
    }

    /**
     * Adds action that will be run just before execution.
     */
    public CompileTransaction beforeExecutionDo(Runnable runnable) {
        beforeExecutionDoActions.add(runnable);
        return this;
    }

    /**
     * Adds action that will be run after execution always, even if there is a failure.
     */
    public CompileTransaction afterExecutionAlwaysDo(Runnable runnable) {
        afterExecutionAlwaysDoActions.add(runnable);
        return this;
    }

    /**
     * Executes the function that is wrapped in the transaction. Function accepts a work result,
     * that has a result of a stash operation. If some files were stashed, then work will be marked as "did work".
     */
    public <T> T execute(Function<WorkResult, T> function) {
        ensureEmptyDirectoriesBeforeAll();
        WorkResult workResult = stashFilesBeforeExecution();
        try {
            beforeExecutionDoActions.forEach(Runnable::run);
            T result = function.apply(workResult);
            onSuccessActions.forEach(Runnable::run);
            return result;
        } catch (Throwable t) {
            if (stashThrowablePredicate.test(t)) {
                stashAfterExecutionActions.forEach(Runnable::run);
            }
            throw t;
        } finally {
            afterExecutionAlwaysDoActions.forEach(Runnable::run);
        }
    }

    private WorkResult stashFilesBeforeExecution() {
        boolean didWork = false;
        for (BooleanSupplier supplier : stashBeforeExecutionActions) {
            didWork |= supplier.getAsBoolean();
        }
        return WorkResults.didWork(didWork);
    }

    private void ensureEmptyDirectoriesBeforeAll() {
        try {
            for (File directory : directoriesToCleanBeforeAll) {
                deleter.ensureEmptyDirectory(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CompileTransaction newTransaction(FileOperations fileOperations, Deleter deleter) {
        return new CompileTransaction(fileOperations, deleter);
    }
}
