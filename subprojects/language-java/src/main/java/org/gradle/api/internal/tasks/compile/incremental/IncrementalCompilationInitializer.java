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

import com.google.common.collect.Lists;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.Collection;
import java.util.List;

class IncrementalCompilationInitializer {
    private final FileOperations fileOperations;

    public IncrementalCompilationInitializer(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    public void initializeCompilation(JavaCompileSpec spec, Collection<String> staleClasses) {
        if (staleClasses.isEmpty()) {
            spec.setSource(new SimpleFileCollection());
            return;
        }

        Factory<PatternSet> patternSetFactory = fileOperations.getFileResolver().getPatternSetFactory();
        PatternSet classesToDelete = patternSetFactory.create();
        PatternSet sourceToCompile = patternSetFactory.create();

        preparePatterns(staleClasses, classesToDelete, sourceToCompile);
        narrowDownSourcesToCompile(spec, sourceToCompile);
        includePreviousCompilationOutputOnClasspath(spec);
        deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());
        deleteStaleFilesIn(classesToDelete, spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory());
    }

    private void narrowDownSourcesToCompile(JavaCompileSpec spec, PatternSet sourceToCompile) {
        spec.setSource(spec.getSource().getAsFileTree().matching(sourceToCompile));
    }

    private void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = Lists.newArrayList(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    private void deleteStaleFilesIn(PatternSet classesToDelete, File destinationDir) {
        if (destinationDir == null) {
            return;
        }
        FileTree deleteMe = fileOperations.fileTree(destinationDir).matching(classesToDelete);
        fileOperations.delete(deleteMe);
    }

    void preparePatterns(Collection<String> staleClasses, PatternSet filesToDelete, PatternSet sourceToCompile) {
        for (String staleClass : staleClasses) {
            String path = staleClass.replaceAll("\\.", "/");
            filesToDelete.include(path.concat(".class"));
            filesToDelete.include(path.concat(".java"));
            filesToDelete.include(path.concat("$*.class"));
            filesToDelete.include(path.concat("$*.java"));

            sourceToCompile.include(path.concat(".java"));
            sourceToCompile.include(path.concat("$*.java"));
        }
    }
}
