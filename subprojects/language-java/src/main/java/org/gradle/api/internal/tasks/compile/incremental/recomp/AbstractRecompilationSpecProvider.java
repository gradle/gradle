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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileType;
import org.gradle.api.internal.file.FileOperations;
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
import java.util.LinkedHashSet;
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
        processSourceChanges(current, previous, spec, sourceFileClassNameConverter);
        collectSourcePathsAndFindIndependentClasses(previous, spec, sourceFileClassNameConverter);
        spec.addClassesToProcess(previous.getTypesToReprocess(spec.getClassesToCompile()));
        return spec;
    }

    /**
     * This method collects all source paths, while collecting paths it additionally also
     * collects all classes that are inside these sources, but were not detected as a dependency of a changed class.
     * This is important so all .class files that will be created are removed before compilation.
     * </p>
     * Example:
     * A.java
     * class A {}
     * </p>
     * Test.java (has C as an independent class)
     * class B extends A {}
     * class C {}
     * </p>
     * If/when we change A, B and C will be recompiled, since we will pass Test.java to a compiler, but we won't delete origin C.class. So C.class will be on classpath at the compile time.
     * That can confuse for example Groovy compiler when it generates test methods for Spock.
     */
    private void collectSourcePathsAndFindIndependentClasses(PreviousCompilation previous, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        SourceFileChangeProcessor sourceFileChangeProcessor = new SourceFileChangeProcessor(previous);
        Set<String> classesToCompile = new LinkedHashSet<>(spec.getClassesToCompile());
        while (!classesToCompile.isEmpty() && !spec.isFullRebuildNeeded()) {

            Set<String> independentClasses = new LinkedHashSet<>();
            for (String classToCompile : classesToCompile) {
                for (String sourcePath : sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile)) {
                    independentClasses.addAll(findIndependentClassesFromSource(sourcePath, spec, sourceFileClassNameConverter));
                    spec.addSourcePath(sourcePath);
                }
            }

            if (independentClasses.isEmpty()) {
                classesToCompile = Collections.emptySet();
            } else {
                // Since these independent classes didn't actually change, they will be just recreated.
                // So we just have to collect just annotation dependencies, so they are correctly deleted before compilation.
                classesToCompile = sourceFileChangeProcessor.processAnnotationDependenciesOfIndependentClasses(independentClasses, spec);
            }
        }
    }

    private Set<String> findIndependentClassesFromSource(String sourcePath, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        Set<String> classNames = sourceFileClassNameConverter.getClassNames(sourcePath);
        if (classNames.size() <= 1) {
            // If we have just 1 class in a source, it was already collected before
            return Collections.emptySet();
        }
        Set<String> newClasses = new LinkedHashSet<>();
        for (String className : classNames) {
            if (spec.addClassesToCompile(className)) {
                newClasses.add(className);
            }
        }
        return newClasses;
    }

    @Override
    public CompileTransaction initCompilationSpecAndTransaction(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.of());
            spec.setClasses(Collections.emptySet());
            return new CompileTransaction(spec, fileOperations.patternSet(), ImmutableMap.of(), fileOperations, deleter);
        }

        PatternSet classesToDelete = fileOperations.patternSet();
        PatternSet sourceToCompile = fileOperations.patternSet();

        prepareFilePatterns(recompilationSpec.getClassesToCompile(), recompilationSpec.getSourcePaths(), classesToDelete, sourceToCompile);
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), fileOperations);
        return new CompileTransaction(spec, classesToDelete, resourcesToDelete, fileOperations, deleter);
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

    private void processSourceChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
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

    private void prepareFilePatterns(Collection<String> staleClasses, Collection<String> sourcePaths, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String sourcePath : sourcePaths) {
            filesToDelete.include(sourcePath);
            sourceToCompile.include(sourcePath);
        }
        for (String staleClass : staleClasses) {
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
