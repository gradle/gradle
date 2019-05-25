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

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import java.util.Set;

/**
 * The strategy used for dynamic processors.
 *
 * @see DynamicProcessor
 */
public class DynamicProcessingStrategy extends IncrementalProcessingStrategy {

    private IncrementalProcessingStrategy delegate;

    DynamicProcessingStrategy(String processorName, AnnotationProcessorResult result) {
        super(result);
        this.delegate = new NonIncrementalProcessingStrategy(processorName, result);
    }

    public void updateFromOptions(Set<String> supportedOptions) {
        if (supportedOptions.contains(IncrementalAnnotationProcessorType.ISOLATING.getProcessorOption())) {
            delegate = new IsolatingProcessingStrategy(result);
        } else if (supportedOptions.contains(IncrementalAnnotationProcessorType.AGGREGATING.getProcessorOption())) {
            delegate = new AggregatingProcessingStrategy(result);
        }
    }

    @Override
    public void recordProcessingInputs(Set<String> supportedAnnotationTypes, Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        delegate.recordProcessingInputs(supportedAnnotationTypes, annotations, roundEnv);
    }

    @Override
    public void recordGeneratedType(CharSequence name, Element[] originatingElements) {
        delegate.recordGeneratedType(name, originatingElements);
    }

    @Override
    public void recordGeneratedResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element[] originatingElements) {
        delegate.recordGeneratedResource(location, pkg, relativeName, originatingElements);
    }
}
