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

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * An isolating processor must provide exactly one originating element
 * for each file it generates.
 */
public class IsolatingProcessor extends DelegatingProcessor {

    private final IsolatingProcessingStrategy strategy;

    public IsolatingProcessor(Processor delegate, AnnotationProcessorResult result) {
        super(delegate);
        this.strategy = new IsolatingProcessingStrategy(result);
    }

    @Override
    public final void init(ProcessingEnvironment processingEnv) {
        IncrementalFiler incrementalFiler = new IncrementalFiler(processingEnv.getFiler(), strategy);
        IncrementalProcessingEnvironment incrementalProcessingEnvironment = new IncrementalProcessingEnvironment(processingEnv, incrementalFiler);
        super.init(incrementalProcessingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv);
        return super.process(annotations, roundEnv);
    }
}
