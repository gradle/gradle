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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;


/**
 * A multiple origin processor can have zero to many originating elements for each generated file.
 */
public final class MultipleOriginProcessor extends DelegatingProcessor {

    private final AnnotationProcessingResult result;

    public MultipleOriginProcessor(Processor delegate, AnnotationProcessingResult result) {
        super(delegate);
        this.result = result;
    }

    @Override
    public final void init(ProcessingEnvironment processingEnv) {
        IncrementalFiler incrementalFiler = new MultipleOriginFiler(processingEnv.getFiler(), result, processingEnv.getMessager());
        IncrementalProcessingEnvironment incrementalProcessingEnvironment = new IncrementalProcessingEnvironment(processingEnv, incrementalFiler);
        super.init(incrementalProcessingEnvironment);
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        recordAggregatedTypes(annotations, roundEnv);
        return super.process(annotations, roundEnv);
    }

    private void recordAggregatedTypes(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (getSupportedAnnotationTypes().contains("*")) {
            result.addAggregatedTypes(ElementUtils.getTopLevelTypeNames(roundEnv.getRootElements()));
        } else {
            for (TypeElement annotation : annotations) {
                result.addAggregatedTypes(ElementUtils.getTopLevelTypeNames(roundEnv.getElementsAnnotatedWith(annotation)));
            }
        }
    }
}
