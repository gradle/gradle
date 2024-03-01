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

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * An annotation processor that did not opt into incremental processing.
 * Any use of such a processor will result in full recompilation.
 * As opposed to the other processor implementations, this one will not
 * decorate the processing environment, because there are some processors
 * that cast it to its implementation type, e.g. JavacProcessingEnvironment.
 */
public class NonIncrementalProcessor extends DelegatingProcessor {

    private final NonIncrementalProcessingStrategy strategy;

    public NonIncrementalProcessor(Processor delegate, AnnotationProcessorResult result) {
        super(delegate);
        this.strategy = new NonIncrementalProcessingStrategy(delegate.getClass().getName(), result);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        strategy.recordProcessingInputs(getSupportedAnnotationTypes(), annotations, roundEnv);
        return super.process(annotations, roundEnv);
    }
}
