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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Map;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.Stash;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.time.Time;
import org.gradle.incap.IncapBuildClientFactory;
import org.gradle.incap.impl.data.GeneratedClassFile;
import org.gradle.incap.impl.data.GeneratedFile;
import org.gradle.incap.impl.data.GeneratedSourceFile;
import org.gradle.incap.impl.data.InputType;
import org.gradle.internal.time.Timer;

import java.io.File;
import java.util.Set;

public class ClassSetAnalysisUpdater {

    private final static Logger LOG = Logging.getLogger(ClassSetAnalysisUpdater.class);
    private static final Predicate<File> IS_CLASS_DIRECTORY = new Predicate<File>() {
        @Override
        public boolean apply(File input) {
            return input.isDirectory();
        }
    };

    private final Stash<ClassSetAnalysisData> stash;
    private final FileOperations fileOperations;
    private ClassDependenciesAnalyzer analyzer;
    private final FileHasher fileHasher;

    public ClassSetAnalysisUpdater(Stash<ClassSetAnalysisData> stash, FileOperations fileOperations, ClassDependenciesAnalyzer analyzer, FileHasher fileHasher) {
        this.stash = stash;
        this.fileOperations = fileOperations;
        this.analyzer = analyzer;
        this.fileHasher = fileHasher;
    }

    public void updateAnalysis(JavaCompileSpec spec) {
        Timer clock = Time.startTimer();
        Set<File> baseDirs = Sets.newLinkedHashSet();
        baseDirs.add(spec.getDestinationDir());
        Iterables.addAll(baseDirs, Iterables.filter(spec.getCompileClasspath(), IS_CLASS_DIRECTORY));
        ClassFilesAnalyzer analyzer = new ClassFilesAnalyzer(this.analyzer, fileHasher);
        for (File baseDir : baseDirs) {
            fileOperations.fileTree(baseDir).visit(analyzer);
        }
        recordGeneratedTypeDependencies(analyzer, spec);
        ClassSetAnalysisData data = analyzer.getAnalysis();
        stash.put(data);
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
    }

    private void recordGeneratedTypeDependencies(ClassFilesAnalyzer analyzer, JavaCompileSpec spec) {
        try {
            Map<InputType, Set<GeneratedFile>> mappings
                = IncapBuildClientFactory.getBuildClient(spec.getIncrementalAnnotationProcessorWorkingDir())
                .readMappingFile().getMapInputTypeToGeneratedFiles();
            for (Map.Entry<InputType, Set<GeneratedFile>> entry : mappings.entrySet()) {
                String generatingType = entry.getKey().getName();
                String generatedType = null;
                for (GeneratedFile output : entry.getValue()) {
                    switch (output.getType()) {
                        case CLASS:
                            generatedType = ((GeneratedClassFile)output).getName().toString();
                            break;
                        case SOURCE:
                            // TODO: Need to get either full filename or (ideally) qname from INCAP.
                            generatedType = ((GeneratedSourceFile)output).getName().toString();
                            break;
                        case RESOURCE:
                            // TODO:  At some point, we need to care about resources.
                            // In the short term we should refuse to allow APs that generate resources.
                            continue;
                    }
                    analyzer.addGeneratorMapping(generatingType, generatedType);
                }
            }
        } catch (IOException iox) {
            // TODO
        }
    }
}
