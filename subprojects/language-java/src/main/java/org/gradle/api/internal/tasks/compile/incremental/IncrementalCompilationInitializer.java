/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class IncrementalCompilationInitializer {
    private final FileOperations fileOperations;
    private final SourceToNameConverter sourceToNameConverter;

    public IncrementalCompilationInitializer(FileOperations fileOperations, SourceToNameConverter sourceToNameConverter) {
        this.fileOperations = fileOperations;
        this.sourceToNameConverter = sourceToNameConverter;
    }

    public void initializeCompilation(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(ImmutableSet.<File>of());
            spec.setClasses(Collections.<String>emptySet());
            return;
        }
        Factory<PatternSet> patternSetFactory = fileOperations.getFileResolver().getPatternSetFactory();
        PatternSet classesToDelete = patternSetFactory.create();

        prepareDeletePatterns(recompilationSpec.getClassesToCompile(), classesToDelete);
        narrowDownSourcesToCompile(spec, recompilationSpec.getClassesToCompile());
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);
        deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());
        deleteStaleFilesIn(classesToDelete, spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory());
    }

    private void narrowDownSourcesToCompile(JavaCompileSpec spec, Collection<String> sourceToCompile) {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        Set<String> classesToCompileSet = ImmutableSet.copyOf(sourceToCompile);
        for (File sourceFile : spec.getSourceFiles()) {
            String className = sourceToNameConverter.getClassName(sourceFile);
            if (classesToCompileSet.contains(className)) {
                builder.add(sourceFile);
            }
        }

        spec.setSourceFiles(builder.build());
    }

    private void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = Lists.newArrayList(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    @VisibleForTesting
    void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = Sets.newHashSet(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClasses(classesToProcess);
    }

    private void deleteStaleFilesIn(PatternSet classesToDelete, File destinationDir) {
        if (destinationDir == null) {
            return;
        }
        FileTree deleteMe = fileOperations.fileTree(destinationDir).matching(classesToDelete);
        fileOperations.delete(deleteMe);
    }

    @VisibleForTesting
    void prepareDeletePatterns(Collection<String> staleClasses, PatternSet filesToDelete) {
        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            filesToDelete.include(path.concat(".class"));
            filesToDelete.include(path.concat(".java"));
            filesToDelete.include(path.concat("$*.class"));
            filesToDelete.include(path.concat("$*.java"));
        }
    }
}
