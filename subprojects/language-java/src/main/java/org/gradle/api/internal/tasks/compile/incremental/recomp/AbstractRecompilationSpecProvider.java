/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileType;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.transaction.CompileTransaction;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.work.FileChange;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.CLASS_OUTPUT;
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.SOURCE_OUTPUT;

abstract class AbstractRecompilationSpecProvider implements RecompilationSpecProvider {
    private final Deleter deleter;
    private final FileOperations fileOperations;
    private final FileTree sourceTree;
    private final Iterable<FileChange> sourceChanges;
    private final boolean incremental;

    public AbstractRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sourceTree,
        Iterable<FileChange> sourceChanges,
        boolean incremental
    ) {
        this.deleter = deleter;
        this.fileOperations = fileOperations;
        this.sourceTree = sourceTree;
        this.sourceChanges = sourceChanges;
        this.incremental = incremental;
    }

    @Override
    public RecompilationSpec provideRecompilationSpec(CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec spec = new RecompilationSpec(previous);
        SourceFileClassNameConverter sourceFileClassNameConverter = getSourceFileClassNameConverter(previous);

        processClasspathChanges(current, previous, spec);
        processOtherChanges(current, previous, spec, sourceFileClassNameConverter);
        spec.addClassesToProcess(previous.getTypesToReprocess(spec.getClassesToCompile()));
        return spec;
    }

    @Override
    public CompileTransaction initCompilationSpecAndTransaction(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            File tempDir = newCompileTransactionTempDir(spec);
            spec.setSourceFiles(ImmutableSet.of());
            spec.setClasses(Collections.emptySet());
            return CompileTransaction.newTransaction(tempDir, fileOperations, deleter);
        }

        PatternSet classesToDelete = fileOperations.patternSet();
        PatternSet sourceToCompile = fileOperations.patternSet();

        SourceFileClassNameConverter sourceFileClassNameConverter = getSourceFileClassNameConverter(recompilationSpec.getPreviousCompilation());
        prepareFilePatterns(recompilationSpec.getClassesToCompile(), classesToDelete, sourceToCompile, sourceFileClassNameConverter);
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), fileOperations);

        File tempDir = newCompileTransactionTempDir(spec);
        File compileDestinationDir = spec.getDestinationDir();
        File annProcessorGeneratedSourcesDir = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory();
        File headerOutputDir = spec.getCompileOptions().getHeaderOutputDirectory();
        return CompileTransaction.newTransaction(tempDir, fileOperations, deleter)
            .onFailureRollbackStashIfException(t -> t instanceof CompilationFailedException)
            // Move files that should be deleted to new folders that will be reverted in case of a failure
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(classesToDelete, compileDestinationDir))
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(classesToDelete, annProcessorGeneratedSourcesDir))
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(classesToDelete, headerOutputDir))
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(resourcesToDelete.get(CLASS_OUTPUT), compileDestinationDir))
            // If the client has not set a location for SOURCE_OUTPUT, javac outputs those files to the CLASS_OUTPUT directory, so delete that instead.
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(resourcesToDelete.get(SOURCE_OUTPUT), MoreObjects.firstNonNull(annProcessorGeneratedSourcesDir, compileDestinationDir)))
            // In the same situation with NATIVE_HEADER_OUTPUT, javac just NPEs.  Don't bother.
            .newTransactionalDirectory(dir -> dir.stashFilesForRollbackOnFailure(resourcesToDelete.get(GeneratedResource.Location.NATIVE_HEADER_OUTPUT), headerOutputDir))
            // Set compile outputs to new folders and in case of a success move content to original folders
            .newTransactionalDirectory(dir -> dir
                .onSuccessMoveFilesTo(compileDestinationDir)
                .beforeExecutionDo(() -> spec.setDestinationDir(dir.getAsFile()))
                .afterExecutionAlwaysDo(() -> spec.setDestinationDir(compileDestinationDir))
            )
            .newTransactionalDirectory(annProcessorGeneratedSourcesDir != null, dir -> dir
                .onSuccessMoveFilesTo(annProcessorGeneratedSourcesDir)
                .beforeExecutionDo(() -> spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(dir.getAsFile()))
                .afterExecutionAlwaysDo(() -> spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annProcessorGeneratedSourcesDir))
            )
            .newTransactionalDirectory(headerOutputDir != null, dir -> dir
                .onSuccessMoveFilesTo(headerOutputDir)
                .beforeExecutionDo(() -> spec.getCompileOptions().setHeaderOutputDirectory(dir.getAsFile()))
                .afterExecutionAlwaysDo(() -> spec.getCompileOptions().setHeaderOutputDirectory(headerOutputDir))
            );
    }

    private File newCompileTransactionTempDir(JavaCompileSpec spec) {
        return new File(spec.getTempDir(), "compileTransaction");
    }

    private Iterable<File> narrowDownSourcesToCompile(FileTree sourceTree, PatternSet sourceToCompile) {
        return sourceTree.matching(sourceToCompile);
    }

    private static Map<GeneratedResource.Location, PatternSet> prepareResourcePatterns(Collection<GeneratedResource> staleResources, FileOperations fileOperations) {
        Map<GeneratedResource.Location, PatternSet> resourcesByLocation = new EnumMap<>(GeneratedResource.Location.class);
        for (GeneratedResource.Location location : GeneratedResource.Location.values()) {
            resourcesByLocation.put(location, fileOperations.patternSet());
        }
        for (GeneratedResource resource : staleResources) {
            resourcesByLocation.get(resource.getLocation()).include(resource.getPath());
        }
        return resourcesByLocation;
    }

    private void processOtherChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        if (spec.isFullRebuildNeeded()) {
            return;
        }
        SourceFileChangeProcessor sourceFileChangeProcessor = new SourceFileChangeProcessor(previous);
        for (FileChange fileChange : sourceChanges) {
            if (spec.isFullRebuildNeeded()) {
                return;
            }
            if (fileChange.getFileType() != FileType.FILE) {
                continue;
            }

            String relativeFilePath = fileChange.getNormalizedPath();
            Set<String> changedClasses = sourceFileClassNameConverter.getClassNames(relativeFilePath);
            if (changedClasses.isEmpty() && !isIncrementalOnResourceChanges(current)) {
                spec.setFullRebuildCause(rebuildClauseForChangedNonSourceFile(fileChange));
            }
            sourceFileChangeProcessor.processChange(changedClasses, spec);
        }
    }

    protected abstract boolean isIncrementalOnResourceChanges(CurrentCompilation currentCompilation);

    private void prepareFilePatterns(Collection<String> staleClasses, PatternSet filesToDelete, PatternSet sourceToCompile, SourceFileClassNameConverter sourceFileClassNameConverter) {
        for (String staleClass : staleClasses) {
            for (String sourcePath : sourceFileClassNameConverter.getRelativeSourcePaths(staleClass)) {
                filesToDelete.include(sourcePath);
                sourceToCompile.include(sourcePath);
            }
            filesToDelete.include(staleClass.replaceAll("\\.", "/").concat(".class"));
            filesToDelete.include(staleClass.replaceAll("[.$]", "_").concat(".h"));
        }
    }

    private void processClasspathChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        DependentsSet dependents = current.findDependentsOfClasspathChanges(previous);
        if (dependents.isDependencyToAll()) {
            spec.setFullRebuildCause(dependents.getDescription());
            return;
        }
        spec.addClassesToCompile(dependents.getPrivateDependentClasses());
        spec.addClassesToCompile(dependents.getAccessibleDependentClasses());
        spec.addResourcesToGenerate(dependents.getDependentResources());
    }

    private void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = new HashSet<>(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClasses(classesToProcess);
    }

    private void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = new ArrayList<>(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    private String rebuildClauseForChangedNonSourceFile(FileChange fileChange) {
        return String.format("%s '%s' has been %s", "resource", fileChange.getFile().getName(), fileChange.getChangeType().name().toLowerCase(Locale.US));
    }

    private SourceFileClassNameConverter getSourceFileClassNameConverter(PreviousCompilation previousCompilation) {
        return new FileNameDerivingClassNameConverter(previousCompilation.getSourceToClassConverter(), getFileExtensions());
    }

    protected abstract Set<String> getFileExtensions();

    @Override
    public boolean isIncremental() {
        return incremental;
    }
}
