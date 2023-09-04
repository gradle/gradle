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

    private static final String MODULE_INFO_CLASS_NAME = "module-info";
    private static final String PACKAGE_INFO_CLASS_NAME = "package-info";

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
    public RecompilationSpec provideRecompilationSpec(JavaCompileSpec spec, CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec recompilationSpec = new RecompilationSpec();
        SourceFileClassNameConverter sourceFileClassNameConverter = new FileNameDerivingClassNameConverter(previous.getSourceToClassConverter(), getFileExtensions());

        processClasspathChanges(current, previous, recompilationSpec);

        SourceFileChangeProcessor sourceFileChangeProcessor = new SourceFileChangeProcessor(previous);
        processSourceChanges(current, sourceFileChangeProcessor, recompilationSpec, sourceFileClassNameConverter);
        processCompilerSpecificDependencies(spec, recompilationSpec, sourceFileChangeProcessor, sourceFileClassNameConverter);
        collectAllSourcePathsAndIndependentClasses(sourceFileChangeProcessor, recompilationSpec, sourceFileClassNameConverter);

        Set<String> typesToReprocess = previous.getTypesToReprocess(recompilationSpec.getClassesToCompile());
        processTypesToReprocess(typesToReprocess, recompilationSpec, sourceFileClassNameConverter);
        addModuleInfoToCompile(recompilationSpec, sourceFileClassNameConverter);

        return recompilationSpec;
    }

    protected abstract Set<String> getFileExtensions();

    private static void processClasspathChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        DependentsSet dependents = current.findDependentsOfClasspathChanges(previous);
        if (dependents.isDependencyToAll()) {
            spec.setFullRebuildCause(dependents.getDescription());
            return;
        }
        spec.addClassesToCompile(dependents.getPrivateDependentClasses());
        spec.addClassesToCompile(dependents.getAccessibleDependentClasses());
        spec.addResourcesToGenerate(dependents.getDependentResources());
    }

    private void processSourceChanges(CurrentCompilation current, SourceFileChangeProcessor sourceFileChangeProcessor, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        if (spec.isFullRebuildNeeded()) {
            return;
        }
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

    private String rebuildClauseForChangedNonSourceFile(FileChange fileChange) {
        return String.format("%s '%s' has been %s", "resource", fileChange.getFile().getName(), fileChange.getChangeType().name().toLowerCase(Locale.US));
    }

    protected abstract void processCompilerSpecificDependencies(
        JavaCompileSpec spec,
        RecompilationSpec recompilationSpec,
        SourceFileChangeProcessor sourceFileChangeProcessor,
        SourceFileClassNameConverter sourceFileClassNameConverter
    );

    protected abstract boolean isIncrementalOnResourceChanges(CurrentCompilation currentCompilation);

    /**
     * This method collects all source paths that will be passed to a compiler. While collecting paths it additionally also
     * collects all classes that are inside these sources, but were not detected as a dependency of changed classes.
     * This is important so all .class files that will be re-created are removed before compilation, otherwise
     * it confuse a compiler: for example Groovy compiler could generate incorrect classes for Spock.
     * <p>
     * We will use name "independent classes" for classes that are in the sources that are passed to a compiler but are not a dependency to a changed class.
     * <p>
     * Check also: <a href="https://github.com/gradle/gradle/issues/21644" />
     */
    private static void collectAllSourcePathsAndIndependentClasses(SourceFileChangeProcessor sourceFileChangeProcessor, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        Set<String> classesToCompile = new LinkedHashSet<>(spec.getClassesToCompile());
        while (!classesToCompile.isEmpty() && !spec.isFullRebuildNeeded()) {
            Set<String> independentClasses = collectSourcePathsAndIndependentClasses(classesToCompile, spec, sourceFileClassNameConverter);
            // Since these independent classes didn't actually change, they will be just recreated without any change, so we don't need to collect all transitive dependencies.
            // But we have to collect annotation processor dependencies, so these classes are correctly deleted, since annotation processor is able to output classes from these independent classes.
            classesToCompile = independentClasses.isEmpty()
                ? Collections.emptySet()
                : sourceFileChangeProcessor.processAnnotationDependenciesOfIndependentClasses(independentClasses, spec);
        }
    }

    /**
     * Collect source paths and independent classes.
     * <p>
     * The source paths corresponding to the {@param classesToCompile} are added to the {@param spec}.
     * It will also add the independent classes to the {@param spec}'s {@code classesToCompile}.
     *
     * @return independent classes for the detected source paths.
     */
    private static Set<String> collectSourcePathsAndIndependentClasses(Set<String> classesToCompile, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        Set<String> independentClasses = new LinkedHashSet<>();
        for (String classToCompile : classesToCompile) {
            for (String sourcePath : sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile)) {
                independentClasses.addAll(collectIndependentClassesForSourcePath(sourcePath, spec, sourceFileClassNameConverter));
                spec.addSourcePath(sourcePath);
            }
        }
        return independentClasses;
    }

    private static Set<String> collectIndependentClassesForSourcePath(String sourcePath, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        Set<String> classNames = sourceFileClassNameConverter.getClassNames(sourcePath);
        if (classNames.size() <= 1) {
            // If source has just 1 class, it doesn't have any independent class
            return Collections.emptySet();
        }
        Set<String> newClasses = new LinkedHashSet<>();
        for (String className : classNames) {
            if (spec.addClassToCompile(className)) {
                newClasses.add(className);
            }
        }
        return newClasses;
    }

    private static void processTypesToReprocess(Set<String> typesToReprocess, RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        for (String typeToReprocess : typesToReprocess) {
            if (typeToReprocess.endsWith(PACKAGE_INFO_CLASS_NAME) || typeToReprocess.equals(MODULE_INFO_CLASS_NAME)) {
                // Fixes: https://github.com/gradle/gradle/issues/17572
                // package-info classes cannot be passed as classes to reprocess to the Java compiler.
                // Therefore, we need to recompile them every time anything changes if they are processed by an aggregating annotation processor.
                spec.addClassToCompile(typeToReprocess);
                spec.addSourcePaths(sourceFileClassNameConverter.getRelativeSourcePaths(typeToReprocess));
            } else {
                spec.addClassToReprocess(typeToReprocess);
            }
        }
    }

    private static void addModuleInfoToCompile(RecompilationSpec spec, SourceFileClassNameConverter sourceFileClassNameConverter) {
        Set<String> moduleInfoSources = sourceFileClassNameConverter.getRelativeSourcePathsThatExist(MODULE_INFO_CLASS_NAME);
        if (!moduleInfoSources.isEmpty()) {
            // Always recompile module-info.java if present.
            // This solves case for incremental compilation where some package was deleted and exported in module-info, but compilation doesn't fail.
            spec.addClassToCompile(MODULE_INFO_CLASS_NAME);
            spec.addSourcePaths(moduleInfoSources);
        }
    }

    @Override
    public CompileTransaction initCompilationSpecAndTransaction(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.of());
            spec.setClassesToProcess(Collections.emptySet());
            return new CompileTransaction(spec, fileOperations.patternSet(), ImmutableMap.of(), fileOperations, deleter);
        }

        PatternSet classesToDelete = fileOperations.patternSet();
        PatternSet sourceToCompile = fileOperations.patternSet();

        prepareFilePatterns(recompilationSpec.getClassesToCompile(), recompilationSpec.getSourcePaths(), classesToDelete, sourceToCompile);
        spec.setSourceFiles(narrowDownSourcesToCompile(sourceTree, sourceToCompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        spec.setClassesToCompile(recompilationSpec.getClassesToCompile());
        Map<GeneratedResource.Location, PatternSet> resourcesToDelete = prepareResourcePatterns(recompilationSpec.getResourcesToGenerate(), fileOperations);
        return new CompileTransaction(spec, classesToDelete, resourcesToDelete, fileOperations, deleter);
    }

    private static Iterable<File> narrowDownSourcesToCompile(FileTree sourceTree, PatternSet sourceToCompile) {
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

    private static void prepareFilePatterns(Collection<String> staleClasses, Collection<String> sourcePaths, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String sourcePath : sourcePaths) {
            filesToDelete.include(sourcePath);
            sourceToCompile.include(sourcePath);
        }
        for (String staleClass : staleClasses) {
            filesToDelete.include(staleClass.replaceAll("\\.", "/").concat(".class"));
            filesToDelete.include(staleClass.replaceAll("[.$]", "_").concat(".h"));
        }
    }

    private static void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = new HashSet<>(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClassesToProcess(classesToProcess);
    }

    private static void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = new ArrayList<>(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    @Override
    public boolean isIncremental() {
        return incremental;
    }
}
