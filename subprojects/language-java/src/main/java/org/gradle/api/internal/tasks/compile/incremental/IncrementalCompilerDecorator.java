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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompileCaches;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarClasspathSnapshotMaker;
import org.gradle.api.internal.tasks.compile.incremental.jar.PreviousCompilation;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.TextUtil;

import java.util.List;
import java.util.Map;

/**
 * Decorates a non-incremental Java compiler (like javac) so that it can be invoked incrementally.
 */
public class IncrementalCompilerDecorator {

    private static final Logger LOG = Logging.getLogger(IncrementalCompilerDecorator.class);
    private final JarClasspathSnapshotMaker jarClasspathSnapshotMaker;
    private final CompileCaches compileCaches;
    private final CleaningJavaCompiler cleaningCompiler;
    private final String displayName;
    private final RecompilationSpecProvider staleClassDetecter;
    private final ClassSetAnalysisUpdater classSetAnalysisUpdater;
    private final CompilationSourceDirs sourceDirs;
    private final FileCollection annotationProcessorPath;
    private final AnnotationProcessorDetector annotationProcessorDetector;
    private final IncrementalCompilationInitializer compilationInitializer;

    public IncrementalCompilerDecorator(JarClasspathSnapshotMaker jarClasspathSnapshotMaker, CompileCaches compileCaches,
                                        IncrementalCompilationInitializer compilationInitializer, CleaningJavaCompiler cleaningCompiler, String displayName,
                                        RecompilationSpecProvider staleClassDetecter, ClassSetAnalysisUpdater classSetAnalysisUpdater,
                                        CompilationSourceDirs sourceDirs, FileCollection annotationProcessorPath, AnnotationProcessorDetector annotationProcessorDetector) {
        this.jarClasspathSnapshotMaker = jarClasspathSnapshotMaker;
        this.compileCaches = compileCaches;
        this.compilationInitializer = compilationInitializer;
        this.cleaningCompiler = cleaningCompiler;
        this.displayName = displayName;
        this.staleClassDetecter = staleClassDetecter;
        this.classSetAnalysisUpdater = classSetAnalysisUpdater;
        this.sourceDirs = sourceDirs;
        this.annotationProcessorPath = annotationProcessorPath;
        this.annotationProcessorDetector = annotationProcessorDetector;
    }

    public Compiler<JavaCompileSpec> prepareCompiler(IncrementalTaskInputs inputs) {
        Compiler<JavaCompileSpec> compiler = getCompiler(inputs, sourceDirs);
        IncrementalResultStoringDecorator compilationFinalizer = new IncrementalResultStoringDecorator(compiler, jarClasspathSnapshotMaker, classSetAnalysisUpdater);
        return new IncrementalAnnotationProcessingCompiler(compilationFinalizer, annotationProcessorDetector);
    }

    private Compiler<JavaCompileSpec> getCompiler(IncrementalTaskInputs inputs, CompilationSourceDirs sourceDirs) {
        if (!inputs.isIncremental()) {
            LOG.info("{} - is not incremental (e.g. outputs have changed, no previous execution, etc.).", displayName);
            return cleaningCompiler;
        }
        if (!sourceDirs.canInferSourceRoots()) {
            LOG.info("{} - is not incremental. Unable to infer the source directories.", displayName);
            return cleaningCompiler;
        }
        List<AnnotationProcessorDeclaration> nonIncrementalProcessors = getNonIncrementalProcessors();
        if (!nonIncrementalProcessors.isEmpty()) {
            warnAboutNonIncrementalProcessors(nonIncrementalProcessors);
            return cleaningCompiler;
        }
        ClassSetAnalysisData data = compileCaches.getLocalClassSetAnalysisStore().get();
        if (data == null) {
            LOG.info("{} - is not incremental. No class analysis data available from the previous build.", displayName);
            return cleaningCompiler;
        }
        PreviousCompilation previousCompilation = new PreviousCompilation(new ClassSetAnalysis(data), compileCaches.getLocalJarClasspathSnapshotStore(), compileCaches.getJarSnapshotCache());
        return new SelectiveCompiler(inputs, previousCompilation, cleaningCompiler, staleClassDetecter, compilationInitializer, jarClasspathSnapshotMaker);
    }

    private List<AnnotationProcessorDeclaration> getNonIncrementalProcessors() {
        Map<String, AnnotationProcessorDeclaration> allProcessors = annotationProcessorDetector.detectProcessors(annotationProcessorPath);
        List<AnnotationProcessorDeclaration> nonIncrementalProcessors = Lists.newArrayListWithCapacity(allProcessors.size());
        for (AnnotationProcessorDeclaration processor : allProcessors.values()) {
            if (!processor.getType().isIncremental()) {
                nonIncrementalProcessors.add(processor);
            }
        }
        return nonIncrementalProcessors;
    }

    private void warnAboutNonIncrementalProcessors(List<AnnotationProcessorDeclaration> nonIncrementalProcessors) {
        if (LOG.isInfoEnabled()) {
            StringBuilder processorListing = new StringBuilder();
            for (AnnotationProcessorDeclaration processor : nonIncrementalProcessors) {
                processorListing.append(TextUtil.getPlatformLineSeparator()).append('\t').append(processor);
            }
            LOG.info("{} - is not incremental. The following annotation processors don't support incremental compilation:{}", displayName, processorListing);
        }
    }
}
