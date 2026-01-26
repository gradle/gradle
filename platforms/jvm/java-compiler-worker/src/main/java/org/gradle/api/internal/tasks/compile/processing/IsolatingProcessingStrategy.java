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
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import java.util.Set;

import static org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType.ISOLATING;

/**
 * The strategy for isolating annotation processors.
 *
 * @see IsolatingProcessor
 */
class IsolatingProcessingStrategy extends IncrementalProcessingStrategy {

    IsolatingProcessingStrategy(AnnotationProcessorResult result) {
        super(result);
        result.setType(ISOLATING);
    }

    @Override
    public void recordProcessingInputs(Set<String> supportedAnnotationTypes, Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    }

    @Override
    public void recordGeneratedType(CharSequence name, Element[] originatingElements) {
        String generatedType = name.toString();
        Set<String> originatingTypes = ElementUtils.getTopLevelTypeNames(originatingElements);
        int size = originatingTypes.size();
        if (size != 1) {
            result.setFullRebuildCause("the generated type '" + generatedType + "' must have exactly one originating element, but had " + size);
        }
        result.addGeneratedType(generatedType, originatingTypes);
    }

    @Override
    public void recordGeneratedResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element[] originatingElements) {
        GeneratedResource.Location resourceLocation = GeneratedResource.Location.from(location);
        if (resourceLocation == null) {
            result.setFullRebuildCause(location + " is not supported for incremental annotation processing");
            return;
        }
        GeneratedResource generatedResource = new GeneratedResource(resourceLocation, pkg, relativeName);

        Set<String> originatingTypes = ElementUtils.getTopLevelTypeNames(originatingElements);
        int size = originatingTypes.size();
        if (size != 1) {
            result.setFullRebuildCause("the generated resource '" + generatedResource + "' must have exactly one originating element, but had " + size);
        }
        result.addGeneratedResource(generatedResource, originatingTypes);
    }
}
