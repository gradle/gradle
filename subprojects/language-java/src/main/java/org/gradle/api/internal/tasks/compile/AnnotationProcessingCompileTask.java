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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;
import org.gradle.api.internal.tasks.compile.processing.AggregatingProcessor;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.processing.DynamicProcessor;
import org.gradle.api.internal.tasks.compile.processing.IsolatingProcessor;
import org.gradle.api.internal.tasks.compile.processing.NonIncrementalProcessor;
import org.gradle.api.internal.tasks.compile.processing.TimeTrackingProcessor;
import org.gradle.internal.classloader.FilteringClassLoader;
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
 *
 * This class also serves a purpose when incremental annotation processing is not active.
 * It replaces the normal processor discovery, which suffers from file descriptor leaks
 * on Java 8 and below. Our own discovery mechanism does not have that issue.
 *
 * This also prevents the Gradle API from leaking into the annotation processor classpath.
 */
class AnnotationProcessingCompileTask implements JavaCompiler.CompilationTask {

    private final JavaCompiler.CompilationTask delegate;
    private final Set<AnnotationProcessorDeclaration> processorDeclarations;
    private final List<File> annotationProcessorPath;
    private final AnnotationProcessingResult result;

    private URLClassLoader processorClassloader;
    private boolean called;

    AnnotationProcessingCompileTask(JavaCompiler.CompilationTask delegate, Set<AnnotationProcessorDeclaration> processorDeclarations, List<File> annotationProcessorPath, AnnotationProcessingResult result) {
        this.delegate = delegate;
        this.processorDeclarations = processorDeclarations;
        this.annotationProcessorPath = annotationProcessorPath;
        this.result = result;
    }

    @Override
    public void addModules(Iterable<String> moduleNames) {
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
        processorClassloader = createProcessorClassLoader();
        List<Processor> processors = new ArrayList<Processor>(processorDeclarations.size());
        for (AnnotationProcessorDeclaration declaredProcessor : processorDeclarations) {
            AnnotationProcessorResult processorResult = new AnnotationProcessorResult(result, declaredProcessor.getClassName());
            result.getAnnotationProcessorResults().add(processorResult);

            Class<?> processorClass = loadProcessor(declaredProcessor);
            Processor processor = instantiateProcessor(processorClass);
            processor = decorateForIncrementalProcessing(processor, declaredProcessor.getType(), processorResult);
            processor = decorateForTimeTracking(processor, processorResult);
            processors.add(processor);
        }
        delegate.setProcessors(processors);
    }

    private URLClassLoader createProcessorClassLoader() {
        return new URLClassLoader(
            DefaultClassPath.of(annotationProcessorPath).getAsURLArray(),
            new FilteringClassLoader(delegate.getClass().getClassLoader(), getExtraAllowedPackages())
        );
    }

    /**
     * Many popular annotation processors like lombok need access to compiler internals
     * to do their magic, e.g. to inspect or even change method bodies. This is not valid
     * according to the annotation processing spec, but forbidding it would upset a lot of
     * our users.
     */
    private FilteringClassLoader.Spec getExtraAllowedPackages() {
        FilteringClassLoader.Spec spec = new FilteringClassLoader.Spec();
        spec.allowPackage("com.sun.tools.javac");
        spec.allowPackage("com.sun.source");
        return spec;
    }

    private Class<?> loadProcessor(AnnotationProcessorDeclaration declaredProcessor) {
        try {
            return processorClassloader.loadClass(declaredProcessor.getClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Annotation processor '" + declaredProcessor.getClassName() + "' not found");
        }
    }

    private Processor instantiateProcessor(Class<?> processorClass) {
        try {
            return (Processor) processorClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not instantiate annotation processor '" + processorClass.getName() + "'");
        }
    }

    private Processor decorateForIncrementalProcessing(Processor processor, IncrementalAnnotationProcessorType type, AnnotationProcessorResult processorResult) {
        switch (type) {
            case ISOLATING:
                return new IsolatingProcessor(processor, processorResult);
            case AGGREGATING:
                return new AggregatingProcessor(processor, processorResult);
            case DYNAMIC:
                return new DynamicProcessor(processor, processorResult);
            default:
                return new NonIncrementalProcessor(processor, processorResult);
        }
    }

    private Processor decorateForTimeTracking(Processor processor, AnnotationProcessorResult processorResult) {
        return new TimeTrackingProcessor(processor, processorResult);
    }

    private void cleanupProcessors() {
        CompositeStoppable.stoppable(processorClassloader).stop();
    }
}
