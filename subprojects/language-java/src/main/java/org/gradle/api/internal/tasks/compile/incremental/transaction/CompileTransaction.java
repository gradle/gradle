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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * A helper class to handle incremental compilation after a failure: it makes moving files around easier and reverting state easier.
 */
public class CompileTransaction {

    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final File tempDir;
    private final File stashDirectory;
    private final List<StashSource> stashSources;
    private final List<StagedOutput> stagedOutputs;

    private CompileTransaction(
        File tempDir,
        FileOperations fileOperations,
        Deleter deleter,
        File stashDirectory,
        List<StashSource> stashSources,
        List<StagedOutput> stagedOutputs
    ) {
        this.tempDir = tempDir;
        this.fileOperations = fileOperations;
        this.deleter = deleter;
        this.stashDirectory = stashDirectory;
        this.stashSources = stashSources;
        this.stagedOutputs = stagedOutputs;
    }

    /**
     * Executes the function that is wrapped in the transaction. Function accepts a work result,
     * that has a result of a stash operation. If some files were stashed, then work will be marked as "did work".
     *
     * Execute will always clean temp directory, so it is empty before execution.
     */
    public <T> T execute(Function<WorkResult, T> function) {
        ensureEmptyDirectories();
        StashResult stashResult = stashFilesBeforeExecution();
        try {
            setupSpecOutputs();
            T result = function.apply(stashResult.mapToWorkResult());
            deletePotentiallyEmptyDirectories(stashResult.stashedFiles, stashResult.sourceDirectories);
            moveCompileOutputToOriginalFolders();
            return result;
        } catch (Exception t) {
            rollbackStash(stashResult.stashedFiles);
            throw t;
        } finally {
            restoreSpecOutputs();
        }
    }

    private void ensureEmptyDirectories() {
        try {
            tempDir.mkdirs();

            // Create or clean stash and stage directories
            Set<File> ensureEmptyDirectories = new HashSet<>();
            deleter.ensureEmptyDirectory(stashDirectory);
            ensureEmptyDirectories.add(stashDirectory);
            for (StagedOutput output : stagedOutputs) {
                ensureEmptyKeepingFolderStructure(output);
                ensureEmptyDirectories.add(output.stagingDirectory);
            }

            // Delete any other file or directory
            try (Stream<Path> dirStream = Files.list(tempDir.toPath())) {
                dirStream.map(Path::toFile)
                    .filter(file -> !ensureEmptyDirectories.contains(file))
                    .forEach(this::deleteRecursively);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureEmptyKeepingFolderStructure(StagedOutput output) throws IOException {
        Path currentDir = output.stagingDirectory.toPath();
        if (!Files.exists(currentDir)) {
            Files.createDirectory(currentDir);
            return;
        }
        try (Stream<Path> dirStream = Files.walk(currentDir)) {
            // Delete all files and delete all directories
            // that don't exist in sourceDirectory
            dirStream
                // Order files in a direction that we can avoid recursive deletion
                .sorted(Comparator.reverseOrder())
                .filter(path -> !Files.isDirectory(path) || !isDirectoryAlsoInOtherRoot(path, currentDir, output.sourceDirectory))
                .forEach(path -> path.toFile().delete());
        }
    }

    private boolean isDirectoryAlsoInOtherRoot(Path directory, Path root, File otherRoot) {
        Path relativePath = root.relativize(directory);
        File fileInOtherRoot = new File(otherRoot, relativePath.toString());
        return fileInOtherRoot.isDirectory();
    }

    private void deleteRecursively(File file) {
        try {
            deleter.deleteRecursively(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private StashResult stashFilesBeforeExecution() {
        AtomicInteger uniqueId = new AtomicInteger();
        Function<File, String> uniqueNameGenerator = file -> file.getName() + ".uniqueId" + uniqueId.getAndIncrement();
        List<File> sourceDirectories = new ArrayList<>();
        List<StashedFile> stashedFiles = new ArrayList<>();
        for (StashSource source : stashSources) {
            sourceDirectories.add(source.sourceDirectory);
            stashedFiles.addAll(stashFilesFor(source.sourceDirectory, source.filePattern, uniqueNameGenerator));
        }
        return new StashResult(sourceDirectories, stashedFiles);
    }

    private List<StashedFile> stashFilesFor(File sourceDirectory, PatternSet patternSet, Function<File, String> uniqueNameGenerator) {
        List<StashedFile> stashedFiles = new ArrayList<>();
        Set<File> sourceFiles = fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles();
        for (File sourceFile : sourceFiles) {
            File stashedFile = new File(stashDirectory, uniqueNameGenerator.apply(sourceFile));
            moveFile(sourceFile, stashedFile);
            stashedFiles.add(new StashedFile(sourceFile, stashedFile));
        }
        return stashedFiles;
    }

    private void deletePotentiallyEmptyDirectories(List<StashedFile> stashedFiles, List<File> sourceDirectories) {
        Set<File> potentiallyEmptyFolders = stashedFiles.stream()
            .map(file -> file.sourceFile.getParentFile())
            .collect(Collectors.toSet());
        StaleOutputCleaner.cleanEmptyOutputDirectories(deleter, potentiallyEmptyFolders, sourceDirectories);
    }

    private void moveCompileOutputToOriginalFolders() {
        stagedOutputs.forEach(output -> moveAllFilesFromDirectoryTo(output.stagingDirectory, output.sourceDirectory));
    }

    private void moveAllFilesFromDirectoryTo(File sourceDirectory, File destinationDirectory) {
        Path sourcePath = sourceDirectory.toPath();
        try (Stream<Path> dirStream = Files.walk(sourcePath)) {
            dirStream.filter(Files::isRegularFile)
                .forEach(path -> {
                    File newFile = new File(destinationDirectory, sourcePath.relativize(path).toString());
                    moveFile(path.toFile(), newFile);
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void rollbackStash(List<StashedFile> stashedFiles) {
        stashedFiles.forEach(stashedFile -> moveFile(stashedFile.stashFile, stashedFile.sourceFile));
    }

    private boolean moveFile(File sourceFile, File destinationFile) {
        try {
            destinationFile.getParentFile().mkdirs();
            Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setupSpecOutputs() {
        stagedOutputs.forEach(output -> output.setSpecOutput.accept(output.stagingDirectory));
    }

    private void restoreSpecOutputs() {
        stagedOutputs.forEach(output -> output.setSpecOutput.accept(output.sourceDirectory));
    }

    public static CompileTransactionBuilder builder(File tempDir, FileOperations fileOperations, Deleter deleter) {
        return new CompileTransactionBuilder(tempDir, fileOperations, deleter);
    }

    public static class CompileTransactionBuilder {
        private final Deleter deleter;
        private final FileOperations fileOperations;
        private final File tempDir;
        private final File stashDirectory;
        private final AtomicInteger uniqueStageId;
        private final List<StashSource> stashSources;
        private final List<StagedOutput> stagedOutputs;

        private CompileTransactionBuilder(File tempDir, FileOperations fileOperations, Deleter deleter) {
            this.tempDir = tempDir;
            this.deleter = deleter;
            this.stashDirectory = new File(tempDir, "stash-dir");
            this.fileOperations = fileOperations;
            this.uniqueStageId = new AtomicInteger();
            this.stashSources = new ArrayList<>();
            this.stagedOutputs = new ArrayList<>();
        }

        public CompileTransactionBuilder stashFiles(PatternSet filesPattern, File sourceDirectory) {
            if (filesPattern != null && !filesPattern.isEmpty() && sourceDirectory != null && sourceDirectory.exists()) {
                stashSources.add(new StashSource(filesPattern, sourceDirectory));
            }
            return this;
        }

        public CompileTransactionBuilder stageOutputDirectory(File sourceDirectory, Consumer<File> setSpecOutputFunction) {
            // Generate unique id always, so directory mapping is somewhat stable
            int uniqueId = uniqueStageId.getAndIncrement();
            if (sourceDirectory != null) {
                File stageDirectory = new File(tempDir, "staging-dir-" + uniqueId);
                stagedOutputs.add(new StagedOutput(sourceDirectory, stageDirectory, setSpecOutputFunction));
            }
            return this;
        }

        public CompileTransaction build() {
            return new CompileTransaction(tempDir, fileOperations, deleter, stashDirectory, stashSources, stagedOutputs);
        }
    }

    private static class StashResult {

        private final List<File> sourceDirectories;
        private final List<StashedFile> stashedFiles;

        private StashResult(List<File> sourceDirectories, List<StashedFile> stashedFiles) {
            this.sourceDirectories = sourceDirectories;
            this.stashedFiles = stashedFiles;
        }

        public WorkResult mapToWorkResult() {
            return WorkResults.didWork(!stashedFiles.isEmpty());
        }
    }

    private static class StagedOutput {
        private final File sourceDirectory;
        private final File stagingDirectory;
        private final Consumer<File> setSpecOutput;

        private StagedOutput(File sourceDirectory, File stagingDirectory, Consumer<File> setSpecOutput) {
            this.sourceDirectory = sourceDirectory;
            this.stagingDirectory = stagingDirectory;
            this.setSpecOutput = setSpecOutput;
        }
    }

    private static class StashSource {
        private final PatternSet filePattern;
        private final File sourceDirectory;

        private StashSource(PatternSet filePattern, File sourceDirectory) {
            this.filePattern = filePattern;
            this.sourceDirectory = sourceDirectory;
        }
    }

    private static class StashedFile {
        private final File sourceFile;
        private final File stashFile;

        private StashedFile(File sourceFile, File stashFile) {
            this.sourceFile = sourceFile;
            this.stashFile = stashFile;
        }
    }
}
