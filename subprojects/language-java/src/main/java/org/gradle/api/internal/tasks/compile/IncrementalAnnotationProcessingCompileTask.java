/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType;
import org.gradle.api.internal.tasks.compile.processing.MultipleOriginProcessor;
import org.gradle.api.internal.tasks.compile.processing.SingleOriginProcessor;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wraps another {@link JavaCompiler.CompilationTask} and sets up its annotation processors
 * according to the provided processor declarations and processor path. Incremental processors
 * are decorated in order to validate their behavior.
 */
class IncrementalAnnotationProcessingCompileTask implements JavaCompiler.CompilationTask {

    private final JavaCompiler.CompilationTask delegate;
    private final Set<AnnotationProcessorDeclaration> processorDeclarations;
    private final List<File> annotationProcessorPath;

    private URLClassLoader processorClassloader;
    private boolean called;

    IncrementalAnnotationProcessingCompileTask(JavaCompiler.CompilationTask delegate, Set<AnnotationProcessorDeclaration> processorDeclarations, List<File> annotationProcessorPath) {
        this.delegate = delegate;
        this.processorDeclarations = processorDeclarations;
        this.annotationProcessorPath = annotationProcessorPath;
    }

    @Override
    public void setProcessors(Iterable<? extends Processor> processors) {
        throw new UnsupportedOperationException("This decorator already handles annotation processing");
    }

    @Override
    public void setLocale(Locale locale) {
        delegate.setLocale(locale);
    }

    @Override
    public Boolean call() {
        if (called) {
            throw new IllegalStateException("Cannot reuse a compilation task");
        }
        called = true;
        try {
            setupProcessors();
            return delegate.call();
        } finally {
            cleanupProcessors();
        }
    }

    private void setupProcessors() {
        processorClassloader = new URLClassLoader(DefaultClassPath.of(annotationProcessorPath).getAsURLArray());
        List<Processor> processors = new ArrayList<Processor>(processorDeclarations.size());
        for (AnnotationProcessorDeclaration declaredProcessor : processorDeclarations) {
            try {
                Class<?> processorClass = processorClassloader.loadClass(declaredProcessor.getClassName());
                Processor processor = (Processor) processorClass.newInstance();
                processor = decorateIfIncremental(processor, declaredProcessor.getType());
                processors.add(processor);
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        delegate.setProcessors(processors);
    }

    private Processor decorateIfIncremental(Processor processor, IncrementalAnnotationProcessorType type) {
        switch (type) {
            case SINGLE_ORIGIN:
                return new SingleOriginProcessor(processor);
            case MULTIPLE_ORIGIN:
                return new MultipleOriginProcessor(processor);
            default:
                return processor;
        }
    }

    private void cleanupProcessors() {
        CompositeStoppable.stoppable(processorClassloader).stop();
    }
}
