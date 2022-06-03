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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final List<Runnable> stashBeforeExecutionActions = new ArrayList<>();
    private final List<Runnable> stashAfterExecutionActions = new ArrayList<>();
    private final List<Runnable> onSuccessActions = new ArrayList<>();
    private final List<Runnable> afterExecutionAlwaysDoActions = new ArrayList<>();
    private Predicate<Throwable> stashThrowablePredicate = t -> true;

    private CompileTransaction(FileOperations fileOperations, Deleter deleter) {
        this.fileOperations = fileOperations;
        this.deleter = deleter;
    }

    /**
     * Directories that should be deleted before the transaction. Normally stash and staging directories should be deleted, so they are clean for a new transaction.
     */
    public CompileTransaction beforeAllRecursivelyDeleteDirectories(File... directories) {
        directoriesToCleanBeforeAll.addAll(Arrays.asList(directories));
        return this;
    }

    /**
     * Stash a pattern set of files from source directory to stash directory. Files are moved before execution and on failure rolled back.
     */
    public CompileTransaction stashStaleFilesTo(PatternSet files, File sourceDirectory, File stashDirectory) {
        return this;
    }

    /**
     * Stash a set of files from source directory to stash directory. Files are moved before execution and on failure rolled back.
     */
    public CompileTransaction stashStaleFilesTo(Set<File> files, File sourceDirectory, File stashDirectory) {
        return this;
    }

    /**
     * Moves files from source directory to destination directory, but only on success. Any file that already exist in destination directory is replaced.
     * Folder hierarchy is automatically created.
     */
    public CompileTransaction onSuccessMoveFilesFromDirectoryTo(File sourceDirectory, File destinationDirectory) {
        onSuccessActions.add(() -> fileOperations.fileTree(sourceDirectory).visit(fileVisitDetails -> {
            if (!fileVisitDetails.isDirectory()) {
                File newFile = new File(destinationDirectory, fileVisitDetails.getPath());
                FileUtils.moveFile(fileVisitDetails.getFile(), newFile);
            }
        }));
        return this;
    }

    /**
     * Adds a predicate to test if transaction should roll back stash or not.
     */
    public CompileTransaction onFailureRollbackStashIfException(Predicate<Throwable> predicate) {
        stashThrowablePredicate = checkNotNull(predicate);
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
        deleteDirectoriesBeforeAll();
        stashBeforeExecutionActions.forEach(Runnable::run);
        try {
            T result = function.apply(WorkResults.didWork(true));
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

    private void deleteDirectoriesBeforeAll() {
        try {
            for (File directory : directoriesToCleanBeforeAll) {
                deleter.deleteRecursively(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CompileTransaction newTransaction(FileOperations fileOperations, Deleter deleter) {
        return new CompileTransaction(fileOperations, deleter);
    }
}
