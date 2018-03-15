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

package org.gradle.api.internal.tasks.compile.processing

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult
import spock.lang.Specification

import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement

class MultipleOriginProcessorTest extends Specification {

    Set<TypeElement> annotationTypes = [
        type("Helper"),
        type("Service")
    ] as Set

    RoundEnvironment roundEnvironment = Stub(RoundEnvironment) {
        getRootElements() >> ([type("A"), type("B"), type("C")] as Set)
        getElementsAnnotatedWith(_ as TypeElement) >> { TypeElement annotationType ->
            if (annotationType.is(annotationTypes[0])) {
                [type("A")] as Set
            } else if (annotationType.is(annotationTypes[1])) {
                [type("B")] as Set
            }
        }
    }

    AnnotationProcessingResult result = new AnnotationProcessingResult()
    Processor delegate = Stub(Processor)
    MultipleOriginProcessor processor = new MultipleOriginProcessor(delegate, result)

    def "when delegate reacts to any class, all root elements are aggregated"() {
        given:
        delegate.getSupportedAnnotationTypes() >> ["*"]

        when:
        processor.process(annotationTypes, roundEnvironment)

        then:
        result.getAggregatedTypes() == ["A", "B", "C"] as Set
    }

    def "when delegate reacts to specific annotatitons, only types annotated with those are aggregated"() {
        given:
        delegate.getSupportedAnnotationTypes() >> annotationTypes.collect { it.getQualifiedName().toString() }

        when:
        processor.process(annotationTypes, roundEnvironment)

        then:
        result.getAggregatedTypes() == ["A", "B"] as Set
    }


    TypeElement type(String typeName) {
        Stub(TypeElement) {
            getEnclosingElement() >> null
            getQualifiedName() >> Stub(Name) {
                toString() >> typeName
            }
        }
    }

}
