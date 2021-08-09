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
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
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
    public boolean initializeCompilation(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.of());
            spec.setClasses(Collections.emptySet());
            return false;
        }
        PatternSet classesToDelete = fileOperations.patternSet();
        PatternSet sourceToCompile = fileOperations.patternSet();

        SourceFileClassNameConverter sourceFileClassNameConverter = getSourceFileClassNameConverter(recompilationSpec.getPreviousCompilation());

        prepareFilePatterns(recompilationSpec.getClassesToCompile(), classesToDelete, sourceToCompile, sourceFileClassNameConverter);
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);

        boolean cleanedAnyOutput = deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());
        cleanedAnyOutput |= deleteStaleFilesIn(classesToDelete, spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory());
        cleanedAnyOutput |= deleteStaleFilesIn(classesToDelete, spec.getCompileOptions().getHeaderOutputDirectory());

        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), fileOperations);
        cleanedAnyOutput |= deleteStaleFilesIn(resourcesToDelete.get(GeneratedResource.Location.CLASS_OUTPUT), spec.getDestinationDir());
        // If the client has not set a location for SOURCE_OUTPUT, javac outputs those files to the CLASS_OUTPUT directory, so clean that instead.
        cleanedAnyOutput |= deleteStaleFilesIn(resourcesToDelete.get(GeneratedResource.Location.SOURCE_OUTPUT), MoreObjects.firstNonNull(spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory(), spec.getDestinationDir()));
        // In the same situation with NATIVE_HEADER_OUTPUT, javac just NPEs.  Don't bother.
        cleanedAnyOutput |= deleteStaleFilesIn(resourcesToDelete.get(GeneratedResource.Location.NATIVE_HEADER_OUTPUT), spec.getCompileOptions().getHeaderOutputDirectory());

        return cleanedAnyOutput;
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

    private boolean deleteStaleFilesIn(PatternSet filesToDelete, final File destinationDir) {
        if (filesToDelete == null || filesToDelete.isEmpty() || destinationDir == null) {
            return false;
        }
        Set<File> toDelete = fileOperations.fileTree(destinationDir).matching(filesToDelete).getFiles();
        return StaleOutputCleaner.cleanOutputs(deleter, toDelete, destinationDir);
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
