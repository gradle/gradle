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

package org.gradle.api.internal.tasks.compile.processing;

import javax.annotation.processing.Completion;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * The base class for all incremental annotation processors. This class
 * decorates the {@link Filer} before passing it to the underlying annotation
 * processor, so we can validate what the processor is doing.
 */
abstract class IncrementalProcessor implements Processor {
    private Processor delegate;

    IncrementalProcessor(Processor delegate) {
        this.delegate = delegate;
    }

    @Override
    public final Set<String> getSupportedOptions() {
        return delegate.getSupportedOptions();
    }

    @Override
    public final Set<String> getSupportedAnnotationTypes() {
        return delegate.getSupportedAnnotationTypes();
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return delegate.getSupportedSourceVersion();
    }

    @Override
    public final void init(ProcessingEnvironment processingEnv) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();
        IncrementalFiler incrementalFiler = wrapFiler(filer, messager);
        IncrementalProcessingEnvironment incrementalProcessingEnvironment = new IncrementalProcessingEnvironment(processingEnv, incrementalFiler);
        delegate.init(incrementalProcessingEnvironment);
    }

    abstract IncrementalFiler wrapFiler(Filer filer, Messager messager);

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
    }

    @Override
    public final Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return delegate.getCompletions(element, annotation, member, userText);
    }
}
