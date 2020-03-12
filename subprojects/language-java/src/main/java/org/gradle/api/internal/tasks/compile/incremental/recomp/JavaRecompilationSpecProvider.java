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
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;
import org.gradle.work.FileChange;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.internal.FileUtils.hasExtension;

public class JavaRecompilationSpecProvider extends AbstractRecompilationSpecProvider {
    private final boolean incremental;
    private final Iterable<FileChange> sourceChanges;
    private final SourceFileClassNameConverter sourceFileClassNameConverter;

    public JavaRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sourceTree,
        boolean incremental,
        Iterable<FileChange> sourceFileChanges,
        SourceFileClassNameConverter sourceFileClassNameConverter) {
        super(deleter, fileOperations, sourceTree);
        this.incremental = incremental;
        this.sourceChanges = sourceFileChanges;
        this.sourceFileClassNameConverter = sourceFileClassNameConverter;
    }

    @Override
    public boolean isIncremental() {
        return incremental;
    }

    @Override
    public RecompilationSpec provideRecompilationSpec(CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec spec = new RecompilationSpec();
        if (sourceFileClassNameConverter.isEmpty()) {
            String fullRebuildCause = previous.getAnnotationProcessorFullRebuildCause();
            if (fullRebuildCause == null) {
                fullRebuildCause = "unable to get source-classes mapping relationship from last compilation";
            }
            spec.setFullRebuildCause(fullRebuildCause, null);
            return spec;
        }

        processClasspathChanges(current, previous, spec);
        processOtherChanges(current, previous, spec);

        spec.getClassesToProcess().addAll(previous.getTypesToReprocess());
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

        prepareFilePatterns(recompilationSpec.getRelativeSourcePathsToCompile(), classesToDelete, sourceToCompile);
        prepareJavaPatterns(recompilationSpec.getClassesToCompile(), classesToDelete, sourceToCompile);
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

    private void processOtherChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        if (spec.isFullRebuildNeeded()) {
            return;
        }
        boolean emptyAnnotationProcessorPath = current.getAnnotationProcessorPath().isEmpty();
        SourceFileChangeProcessor sourceFileChangeProcessor = new SourceFileChangeProcessor(previous);
        for (FileChange fileChange : sourceChanges) {
            if (spec.isFullRebuildNeeded()) {
                return;
            }
            if (fileChange.getFileType() != FileType.FILE) {
                continue;
            }

            File changedFile = fileChange.getFile();
            if (hasExtension(changedFile, ".java")) {
                String relativeFilePath = fileChange.getNormalizedPath();

                Collection<String> changedClasses = sourceFileClassNameConverter.getClassNames(relativeFilePath);
                spec.getRelativeSourcePathsToCompile().add(relativeFilePath);
                sourceFileChangeProcessor.processChange(changedFile, changedClasses, spec);
            } else {
                if (emptyAnnotationProcessorPath) {
                    continue;
                }
                spec.setFullRebuildCause(rebuildClauseForChangedNonSourceFile("resource", fileChange), null);
                return;
            }
        }

        for (String className : spec.getClassesToCompile()) {
            if (spec.isFullRebuildNeeded()) {
                return;
            }

            Optional<String> relativeSourceFile = sourceFileClassNameConverter.getRelativeSourcePath(className);
            relativeSourceFile.ifPresent(s -> spec.getRelativeSourcePathsToCompile().add(s));
        }
    }

    private void prepareFilePatterns(Set<String> relativeSourcePathsToCompile, PatternSet classesToDelete, PatternSet filesToRecompilePatterns) {
        for (String relativeSourcePath : relativeSourcePathsToCompile) {
            filesToRecompilePatterns.include(relativeSourcePath);

            sourceFileClassNameConverter.getClassNames(relativeSourcePath)
                .stream()
                .map(staleClass -> staleClass.replaceAll("\\.", "/").concat(".class"))
                .forEach(classesToDelete::include);
        }
    }


    private void prepareJavaPatterns(Collection<String> staleClasses, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            String headerPath = staleClass.replaceAll("\\.", "_");
            filesToDelete.include(path.concat(".class"));
            filesToDelete.include(path.concat(".java"));
            filesToDelete.include(headerPath.concat(".h"));
            filesToDelete.include(path.concat("$*.class"));
            filesToDelete.include(path.concat("$*.java"));
            filesToDelete.include(headerPath.concat("_*.h"));

            sourceToCompile.include(path.concat(".java"));
            sourceToCompile.include(path.concat("$*.java"));
        }
    }
}
