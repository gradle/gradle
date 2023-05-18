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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.ApiCompilerResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final Logger LOG = LoggerFactory.getLogger(CompileTransaction.class);

    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final PatternSet classesToDelete;
    private final JavaCompileSpec spec;
    private final Map<GeneratedResource.Location, PatternSet> resourcesToDelete;
    private final File stashDirectory;
    private final File tempDir;
    private final File backupDirectory;

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
        this.backupDirectory = new File(tempDir, "backup-dir");
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
     * 3. a. In case of a success do nothing <br>
     *    b. In case of a failure delete generated files and restore stashed files <br>
     */
    public <T> T execute(Function<WorkResult, T> function) {
        ensureEmptyDirectoriesBeforeExecution();
        List<StashedFile> stashedFiles = stashFilesThatShouldBeDeleted();
        try {
            if (supportsIncrementalCompilationAfterFailure()) {
                spec.setClassBackupDir(backupDirectory);
            }
            T result = function.apply(WorkResults.didWork(!stashedFiles.isEmpty()));
            deleteEmptyDirectoriesAfterCompilation(stashedFiles);
            return result;
        } catch (CompilationFailedException e) {
            if (supportsIncrementalCompilationAfterFailure()) {
                rollback(stashedFiles, e.getCompilerPartialResult().orElse(null));
            }
            throw e;
        }
    }

    private boolean supportsIncrementalCompilationAfterFailure() {
        return spec.getCompileOptions().supportsIncrementalCompilationAfterFailure();
    }

    private void ensureEmptyDirectoriesBeforeExecution() {
        try {
            tempDir.mkdirs();

            // Create or clean stash and stage directories
            Set<File> ensureEmptyDirectories = new HashSet<>();
            deleter.ensureEmptyDirectory(stashDirectory);
            ensureEmptyDirectories.add(stashDirectory);
            deleter.ensureEmptyDirectory(backupDirectory);
            ensureEmptyDirectories.add(backupDirectory);

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

    private void deleteRecursively(File file) {
        try {
            deleter.deleteRecursively(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<StashedFile> stashFilesThatShouldBeDeleted() {
        int uniqueId = 0;
        List<StashedFile> stashedFiles = new ArrayList<>();
        for (File fileToDelete : collectFilesToDelete(classesToDelete, resourcesToDelete)) {
            File stashedFile = new File(stashDirectory, fileToDelete.getName() + ".uniqueId" + uniqueId++);
            moveFile(fileToDelete, stashedFile);
            stashedFiles.add(new StashedFile(fileToDelete, stashedFile));
        }
        return stashedFiles;
    }

    private void deleteEmptyDirectoriesAfterCompilation(List<StashedFile> stashedFiles) {
        ImmutableSet<File> outputDirectories = getOutputDirectories();
        Set<File> potentiallyEmptyFolders = stashedFiles.stream()
            .map(file -> file.sourceFile.getParentFile())
            .collect(Collectors.toSet());
        StaleOutputCleaner.cleanEmptyOutputDirectories(deleter, potentiallyEmptyFolders, outputDirectories);
    }

    private void rollback(List<StashedFile> stashResult, @Nullable ApiCompilerResult compilerResult) {
        if (compilerResult != null) {
            deleteGeneratedFiles(compilerResult);
            rollbackOverwrittenFiles(compilerResult);
        }
        rollbackStashedFiles(stashResult);
    }

    private void deleteGeneratedFiles(ApiCompilerResult compilerResult) {
        PatternSet classesToDelete = getNewGeneratedClasses(compilerResult);
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = getNewGeneratedResources(compilerResult);
        Set<File> filesToDelete = collectFilesToDelete(classesToDelete, resourcesToDelete);
        StaleOutputCleaner.cleanOutputs(deleter, filesToDelete, getOutputDirectories());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Deleting generated files: {}", filesToDelete.stream().sorted().collect(Collectors.toList()));
        }
    }

    private PatternSet getNewGeneratedClasses(ApiCompilerResult result) {
        PatternSet filesToDelete = fileOperations.patternSet();
        result.getSourceClassesMapping().values().stream()
            .flatMap(Collection::stream)
            .forEach(className -> {
                filesToDelete.include(className.replace(".", "/").concat(".class"));
                filesToDelete.include(className.replaceAll("[.$]", "_").concat(".h"));
            });
        Set<String> annotationProcessorTypes = new HashSet<>(result.getAnnotationProcessingResult().getGeneratedAggregatingTypes());
        result.getAnnotationProcessingResult().getGeneratedTypesWithIsolatedOrigin().values().stream()
            .flatMap(Collection::stream)
            .forEach(annotationProcessorTypes::add);
        annotationProcessorTypes.forEach(className -> {
            filesToDelete.include(className.replace(".", "/").concat(".class"));
            filesToDelete.include(className.replaceAll("[.$]", "_").concat(".h"));
            filesToDelete.include(className.replace(".", "/").concat(".java"));
        });
        return filesToDelete;
    }

    private Map<GeneratedResource.Location, PatternSet> getNewGeneratedResources(ApiCompilerResult result) {
        Map<GeneratedResource.Location, PatternSet> resourcesByLocation = new EnumMap<>(GeneratedResource.Location.class);
        Stream.of(GeneratedResource.Location.values()).forEach(location -> resourcesByLocation.put(location, fileOperations.patternSet()));
        result.getAnnotationProcessingResult()
            .getGeneratedAggregatingResources()
            .forEach(resource -> resourcesByLocation.get(resource.getLocation()).include(resource.getPath()));
        result.getAnnotationProcessingResult().getGeneratedResourcesWithIsolatedOrigin().values().stream()
            .flatMap(Collection::stream)
            .forEach(resource -> resourcesByLocation.get(resource.getLocation()).include(resource.getPath()));
        return resourcesByLocation;
    }

    private Set<File> collectFilesToDelete(PatternSet classesToDelete, Map<GeneratedResource.Location, PatternSet> resourcesToDelete) {
        File compileOutput = spec.getDestinationDir();
        File annotationProcessorOutput = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        File headerOutput = spec.getCompileOptions().getHeaderOutputDirectory();
        Set<File> filesToDelete = new HashSet<>();
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, compileOutput));
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, annotationProcessorOutput));
        filesToDelete.addAll(collectFilesToDelete(classesToDelete, headerOutput));
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(CLASS_OUTPUT), compileOutput));
        // If the client has not set a location for SOURCE_OUTPUT, javac outputs those files to the CLASS_OUTPUT directory, so delete that instead.
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(SOURCE_OUTPUT), MoreObjects.firstNonNull(annotationProcessorOutput, compileOutput)));
        // In the same situation with NATIVE_HEADER_OUTPUT, javac just NPEs.  Don't bother.
        filesToDelete.addAll(collectFilesToDelete(resourcesToDelete.get(NATIVE_HEADER_OUTPUT), headerOutput));
        return filesToDelete;
    }

    private Set<File> collectFilesToDelete(PatternSet patternSet, File sourceDirectory) {
        if (patternSet != null && !patternSet.isEmpty() && sourceDirectory != null && sourceDirectory.exists()) {
            return fileOperations.fileTree(sourceDirectory).matching(patternSet).getFiles();
        }
        return Collections.emptySet();
    }

    private ImmutableSet<File> getOutputDirectories() {
        return Stream.of(spec.getDestinationDir(), spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory(), spec.getCompileOptions().getHeaderOutputDirectory())
            .filter(Objects::nonNull)
            .collect(ImmutableSet.toImmutableSet());
    }

    private static void rollbackOverwrittenFiles(ApiCompilerResult result) {
        result.getBackupClassFiles().forEach((original, backup) -> moveFile(new File(backup), new File(original)));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Restoring overwritten files: {}", result.getBackupClassFiles().keySet().stream().sorted().collect(Collectors.toList()));
        }
    }

    private static void rollbackStashedFiles(List<StashedFile> stashedFiles) {
        stashedFiles.forEach(StashedFile::unstash);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Restoring stashed files: {}", stashedFiles.stream().map(f -> f.sourceFile.getAbsolutePath()).sorted().collect(Collectors.toList()));
        }
    }

    private static void moveFile(File sourceFile, File destinationFile) {
        try {
            destinationFile.getParentFile().mkdirs();
            Files.move(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
