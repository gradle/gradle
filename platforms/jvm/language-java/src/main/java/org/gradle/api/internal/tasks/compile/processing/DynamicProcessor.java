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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult;
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * An annotation processor which can decide whether it is isolating, aggregating or non-incremental at runtime.
 * It needs to return its type through the {@link #getSupportedOptions()} method in the format defined by
 * {@link IncrementalAnnotationProcessorType#getProcessorOption()}.
 */
public class DynamicProcessor extends DelegatingProcessor {
    private final DynamicProcessingStrategy strategy;

    public DynamicProcessor(Processor delegate, AnnotationProcessorResult result) {
        super(delegate);
        strategy = new DynamicProcessingStrategy(delegate.getClass().getName(), result);
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        IncrementalFiler incrementalFiler = new IncrementalFiler(processingEnv.getFiler(), strategy);
        IncrementalProcessingEnvironment incrementalEnvironment = new IncrementalProcessingEnvironment(processingEnv, incrementalFiler);
        super.init(incrementalEnvironment);
        strategy.updateFromOptions(getSupportedOptions());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv);
        return super.process(annotations, roundEnv);
    }
}
