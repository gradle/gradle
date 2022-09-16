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

import com.google.common.base.MoreObjects;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.CLASS_OUTPUT;
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.NATIVE_HEADER_OUTPUT;
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.SOURCE_OUTPUT;

/**
 * A helper class to handle incremental compilation after a failure: it makes moving files around easier and reverting state easier.
 */
public class CompileTransaction {

    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final PatternSet classesToDelete;
    private final JavaCompileSpec spec;
    private final Map<GeneratedResource.Location, PatternSet> resourcesToDelete;
    private final File stashDirectory;
    private final File tempDir;

    public CompileTransaction(
        JavaCompileSpec spec,
        PatternSet classesToDelete,
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete,
        FileOperations fileOperations,
        Deleter deleter
    ) {
        this.spec = spec;
        this.tempDir = new File(spec.getTempDir(), "compileTransaction");
        this.stashDirectory = new File(tempDir, "stash-dir");
        this.classesToDelete = classesToDelete;
        this.resourcesToDelete = resourcesToDelete;
        this.fileOperations = fileOperations;
        this.deleter = deleter;
    }

    /**
     * Executes the function that is wrapped in the transaction. Function accepts a work result,
     * that has a result of a stash operation. If some files were stashed, then work will be marked as "did work".
     * <p>
     * Execution steps: <br>
     * 1. At start create empty temporary directories or make sure they are empty <br>
     * 2. Stash all files that should be deleted from compiler destination directories to a temporary directories <br>
     * 3. Change the compiler destination directories to a temporary directories (different from the stash step) <br>
     * 4. a. In case of a success copy compiler outputs to original destination directories <br>
     *    b. In case of a failure restore stashed files back to original destination directories <br>
     */
    public <T> T execute(Function<WorkResult, T> function) {
        List<StagedOutput> stagedOutputs = collectOutputsToStage();
        ensureEmptyDirectoriesBeforeExecution(stagedOutputs);
        StashResult stashResult = stashFilesThatShouldBeDeleted();
        try {
            setupSpecOutputs(stagedOutputs);
            T result = function.apply(stashResult.mapToWorkResult());
            deletePotentiallyEmptyDirectories(stashResult);
            moveCompileOutputToOriginalFolders(stagedOutputs);
            return result;
        } catch (CompilationFailedException t) {
            if (spec.getCompileOptions().supportsIncrementalCompilationAfterFailure()) {
                rollbackStash(stashResult.stashedFiles);
            }
            throw t;
        } finally {
            restoreSpecOutputs(stagedOutputs);
        }
    }

    private void ensureEmptyDirectoriesBeforeExecution(List<StagedOutput> stagedOutputs) {
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

    private List<StagedOutput> collectOutputsToStage() {
        List<StagedOutput> stagedOutputs = new ArrayList<>();
        stagedOutputs.add(new StagedOutput(spec.getDestinationDir(), new File(tempDir, "compile-output"), spec::setDestinationDir));

        File annotationOutputDir = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        if (annotationOutputDir != null) {
            StagedOutput stagedOutput = new StagedOutput(annotationOutputDir, new File(tempDir, "annotation-output"), file -> spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(file));
            stagedOutputs.add(stagedOutput);
        }

        File headerOutputDir = spec.getCompileOptions().getHeaderOutputDirectory();
        if (spec.getCompileOptions().getHeaderOutputDirectory() != null) {
            StagedOutput stagedOutput = new StagedOutput(headerOutputDir, new File(tempDir, "header-output"), file -> spec.getCompileOptions().setHeaderOutputDirectory(file));
            stagedOutputs.add(stagedOutput);
        }
        return stagedOutputs;
    }

    private StashResult stashFilesThatShouldBeDeleted() {
        int uniqueId = 0;
        File compileOutput = spec.getDestinationDir();
        File annotationProcessorOutput = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        File headerOutput = spec.getCompileOptions().getHeaderOutputDirectory();
        List<StashedFile> stashedFiles = new ArrayList<>();
        for (File sourceFile : collectFilesToStash(compileOutput, annotationProcessorOutput, headerOutput)) {
            File stashedFile = new File(stashDirectory, sourceFile.getName() + ".uniqueId" + uniqueId++);
            moveFile(sourceFile, stashedFile);
            stashedFiles.add(new StashedFile(sourceFile, stashedFile));
        }
        List<File> sourceDirectories = Stream.of(compileOutput, annotationProcessorOutput, headerOutput)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return new StashResult(sourceDirectories, stashedFiles);
    }

    private Set<File> collectFilesToStash(File compileOutput, @Nullable File annotationProcessorOutput, @Nullable File headerOutput) {
        Set<File> filesToStash = new HashSet<>();
        filesToStash.addAll(collectFilesToStash(classesToDelete, compileOutput));
        filesToStash.addAll(collectFilesToStash(classesToDelete, annotationProcessorOutput));
        filesToStash.addAll(collectFilesToStash(classesToDelete, headerOutput));
        filesToStash.addAll(collectFilesToStash(resourcesToDelete.get(CLASS_OUTPUT), compileOutput));
        // If the client has not set a location for SOURCE_OUTPUT, javac outputs those files to the CLASS_OUTPUT directory, so delete that instead.
        filesToStash.addAll(collectFilesToStash(resourcesToDelete.get(SOURCE_OUTPUT), MoreObjects.firstNonNull(annotationProcessorOutput, compileOutput)));
        // In the same situation with NATIVE_HEADER_OUTPUT, javac just NPEs.  Don't bother.
        filesToStash.addAll(collectFilesToStash(resourcesToDelete.get(NATIVE_HEADER_OUTPUT), headerOutput));
        return filesToStash;
    }

    private Set<File> collectFilesToStash(PatternSet patternSet, File sourceDirectory) {
        if (patternSet != null && !patternSet.isEmpty() && sourceDirectory != null && sourceDirectory.exists()) {
            return fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles();
        }
        return Collections.emptySet();
    }

    private void deletePotentiallyEmptyDirectories(StashResult stashResult) {
        Set<File> potentiallyEmptyFolders = stashResult.stashedFiles.stream()
            .map(file -> file.sourceFile.getParentFile())
            .collect(Collectors.toSet());
        StaleOutputCleaner.cleanEmptyOutputDirectories(deleter, potentiallyEmptyFolders, stashResult.sourceDirectories);
    }

    private void moveCompileOutputToOriginalFolders(List<StagedOutput> stagedOutputs) {
        stagedOutputs.forEach(StagedOutput::unstage);
    }

    private void rollbackStash(List<StashedFile> stashedFiles) {
        stashedFiles.forEach(StashedFile::unstash);
    }

    private void setupSpecOutputs(List<StagedOutput> stagedOutputs) {
        stagedOutputs.forEach(StagedOutput::setupSpecOutput);
    }

    private void restoreSpecOutputs(List<StagedOutput> stagedOutputs) {
        stagedOutputs.forEach(StagedOutput::restoreSpecOutput);
    }

    private static void moveFile(File sourceFile, File destinationFile) {
        try {
            destinationFile.getParentFile().mkdirs();
            Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

        public void setupSpecOutput() {
            setSpecOutput.accept(stagingDirectory);
        }

        public void restoreSpecOutput() {
            setSpecOutput.accept(sourceDirectory);
        }

        public void unstage() {
            Path stagingPath = stagingDirectory.toPath();
            try (Stream<Path> dirStream = Files.walk(stagingPath)) {
                dirStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        File newFile = new File(sourceDirectory, stagingPath.relativize(path).toString());
                        moveFile(path.toFile(), newFile);
                    });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static class StashedFile {
        private final File sourceFile;
        private final File stashFile;

        private StashedFile(File sourceFile, File stashFile) {
            this.sourceFile = sourceFile;
            this.stashFile = stashFile;
        }

        public void unstash() {
            moveFile(stashFile, sourceFile);
        }
    }
}
