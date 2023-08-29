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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType.AGGREGATING;

/**
 * The strategy used for aggregating annotation processors.
 * @see AggregatingProcessor
 */
class AggregatingProcessingStrategy extends IncrementalProcessingStrategy {

    AggregatingProcessingStrategy(AnnotationProcessorResult result) {
        super(result);
        result.setType(AGGREGATING);
    }

    @Override
    public void recordProcessingInputs(Set<String> supportedAnnotationTypes, Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        validateAnnotations(annotations);
        recordAggregatedTypes(supportedAnnotationTypes, annotations, roundEnv);
    }

    private void validateAnnotations(Set<? extends TypeElement> annotations) {
        for (TypeElement annotation : annotations) {
            Retention retention = annotation.getAnnotation(Retention.class);
            if (retention != null && retention.value() == RetentionPolicy.SOURCE) {
                result.setFullRebuildCause("'@" + annotation.getSimpleName() + "' has source retention. Aggregating annotation processors require class or runtime retention");
            }
        }
    }

    private void recordAggregatedTypes(Set<String> supportedAnnotationTypes, Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (supportedAnnotationTypes.contains("*")) {
            result.getAggregatedTypes().addAll(namesOfElements(roundEnv.getRootElements()));
        } else {
            for (TypeElement annotation : annotations) {
                result.getAggregatedTypes().addAll(namesOfElements(roundEnv.getElementsAnnotatedWith(annotation)));
            }
        }
    }

    private static Set<String> namesOfElements(Set<? extends Element> orig) {
        if (orig == null || orig.isEmpty()) {
            return Collections.emptySet();
        }
        return orig
            .stream()
            .map(ElementUtils::getTopLevelType)
            .map(ElementUtils::getElementName)
            .collect(Collectors.toSet());
    }

    @Override
    public void recordGeneratedType(CharSequence name, Element[] originatingElements) {
        result.getGeneratedAggregatingTypes().add(name.toString());
    }

    @Override
    public void recordGeneratedResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element[] originatingElements) {
        GeneratedResource.Location resourceLocation = GeneratedResource.Location.from(location);
        if (resourceLocation == null) {
            result.setFullRebuildCause(location + " is not supported for incremental annotation processing");
        } else {
            result.getGeneratedAggregatingResources().add(new GeneratedResource(resourceLocation, pkg, relativeName));
        }
    }

    @Override
    public String toString() {
        return "Aggregating strategy for " + result.getClassName();
    }
}
