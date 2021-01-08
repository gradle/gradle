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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration;
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sets up annotation processing before delegating to the actual Java compiler.
 */
public class AnnotationProcessorDiscoveringCompiler<T extends JavaCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;
    private final AnnotationProcessorDetector annotationProcessorDetector;

    public AnnotationProcessorDiscoveringCompiler(Compiler<T> delegate, AnnotationProcessorDetector annotationProcessorDetector) {
        this.delegate = delegate;
        this.annotationProcessorDetector = annotationProcessorDetector;
    }

    @Override
    public WorkResult execute(T spec) {
        Set<AnnotationProcessorDeclaration> annotationProcessors = getEffectiveAnnotationProcessors(spec);
        spec.setEffectiveAnnotationProcessors(annotationProcessors);
        return delegate.execute(spec);
    }

    /**
     * Scans the processor path for processor declarations. Filters them if the explicit <code>-processor</code> argument is given.
     * Treats explicit processors that didn't have a matching declaration on the path as non-incremental.
     */
    private Set<AnnotationProcessorDeclaration> getEffectiveAnnotationProcessors(JavaCompileSpec spec) {
        Map<String, AnnotationProcessorDeclaration> declarations = annotationProcessorDetector.detectProcessors(spec.getAnnotationProcessorPath());
        List<String> compilerArgs = spec.getCompileOptions().getCompilerArgs();
        int processorIndex = compilerArgs.lastIndexOf("-processor");
        if (processorIndex == -1) {
            return Sets.newLinkedHashSet(declarations.values());
        }
        if (processorIndex == compilerArgs.size() - 1) {
            throw new InvalidUserDataException("No processor specified for compiler argument -processor in requested compiler args: " + Joiner.on(" ").join(compilerArgs));
        }
        Collection<String> explicitProcessors = Splitter.on(',').splitToList(compilerArgs.get(processorIndex + 1));
        Set<AnnotationProcessorDeclaration> effectiveProcessors = Sets.newLinkedHashSet();
        for (String explicitProcessor : explicitProcessors) {
            AnnotationProcessorDeclaration declaration = declarations.get(explicitProcessor);
            if (declaration != null) {
                effectiveProcessors.add(declaration);
            } else {
                effectiveProcessors.add(new AnnotationProcessorDeclaration(explicitProcessor, IncrementalAnnotationProcessorType.UNKNOWN));
            }
        }
        return effectiveProcessors;
    }
}
