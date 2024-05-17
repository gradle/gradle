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
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import spock.lang.Specification

import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class AggregatingProcessorTest extends Specification {

    Set<TypeElement> annotationTypes = [
        annotation("Helper"),
        annotation("Service")
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
    AnnotationProcessorResult processorResult = new AnnotationProcessorResult(result, "")
    Processor delegate = Stub(Processor)
    AggregatingProcessor processor = new AggregatingProcessor(delegate, processorResult)

    def "sets processor type"() {
        expect:
        processorResult.type == IncrementalAnnotationProcessorType.AGGREGATING
    }

    def "when delegate reacts to any class, all root elements are aggregated"() {
        given:
        delegate.getSupportedAnnotationTypes() >> ["*"]

        when:
        processor.process(annotationTypes, roundEnvironment)

        then:
        result.getAggregatedTypes() == ["A", "B", "C"] as Set
    }

    def "when delegate reacts to specific annotations, only types annotated with those are aggregated"() {
        given:
        delegate.getSupportedAnnotationTypes() >> annotationTypes.collect { it.getQualifiedName().toString() }

        when:
        processor.process(annotationTypes, roundEnvironment)

        then:
        result.getAggregatedTypes() == ["A", "B"] as Set
    }

    def "doesn't aggregated types which have source when annotation isn't at top level"() {
        given:
        delegate.getSupportedAnnotationTypes() >> annotationTypes.collect { it.getQualifiedName().toString() }
        roundEnvironment = Stub(RoundEnvironment) {
            getRootElements() >> ([type("A"), type("B"), type("C")] as Set)
            getElementsAnnotatedWith(_ as TypeElement) >> { TypeElement annotationType ->
                [method(type("A")), type("C")] as Set
            }
        }

        when:
        processor.process(annotationTypes, roundEnvironment)

        then:
        result.getAggregatedTypes() == ["A", "C"] as Set
    }

    def "aggregating processors do not work with source retention annotations"() {
        given:
        def sourceRetentionAnnotation = annotation("Broken", RetentionPolicy.SOURCE)

        when:
        processor.process([sourceRetentionAnnotation] as Set, roundEnvironment)

        then:
        result.fullRebuildCause.contains("'@Broken' has source retention.")
    }


    TypeElement annotation(String name, RetentionPolicy retentionPolicy = RetentionPolicy.CLASS) {
        Stub(TypeElement) {
            getEnclosingElement() >> null
            getQualifiedName() >> Stub(Name) {
                toString() >> name
            }
            getSimpleName() >> Stub(Name) {
                toString() >> name
            }
            getAnnotation(Retention) >> Stub(Retention) {
                value() >> retentionPolicy
            }
        }
    }

    Element method(Element parent) {
        Stub(ExecutableElement) {
            getEnclosingElement() >> parent
        }
    }

    TypeElement type(String name) {
        Stub(TypeElement) {
            getEnclosingElement() >> null
            getQualifiedName() >> {
                Stub(Name) {
                    toString() >> name
                }
            }
        }
    }



}
