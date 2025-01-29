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

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorResult
import spock.lang.Specification

import javax.annotation.processing.Completions
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.NANOSECONDS

class TimeTrackingProcessorTest extends Specification {

    def delegate = Mock(Processor)
    def result = new AnnotationProcessorResult(null, "")
    def currentNanos = 0L
    def tracker = new TimeTrackingProcessor(delegate, result, { currentNanos })

    def "tracks time for getSupportedAnnotationTypes()"() {
        when:
        def types = tracker.getSupportedAnnotationTypes()

        then:
        1 * delegate.getSupportedAnnotationTypes() >> {
            simulateWorkWithDuration(13)
            ['AnnotationType']
        }
        types == ['AnnotationType'] as Set
        result.executionTimeInMillis == 13
    }

    def "tracks time for getSupportedOptions()"() {
        when:
        def options = tracker.getSupportedOptions()

        then:
        1 * delegate.getSupportedOptions() >> {
            simulateWorkWithDuration(17)
            ['Option']
        }
        options == ['Option'] as Set
        result.executionTimeInMillis == 17
    }

    def "tracks time for getSupportedSourceVersion()"() {
        when:
        def sourceVersion = tracker.getSupportedSourceVersion()

        then:
        1 * delegate.getSupportedSourceVersion() >> {
            simulateWorkWithDuration(3)
            SourceVersion.RELEASE_8
        }
        sourceVersion == SourceVersion.RELEASE_8
        result.executionTimeInMillis == 3
    }

    def "tracks time for init()"() {
        def processingEnv = Stub(ProcessingEnvironment)

        when:
        tracker.init(processingEnv)

        then:
        1 * delegate.init(processingEnv) >> {
            simulateWorkWithDuration(5)
        }
        result.executionTimeInMillis == 5
    }

    def "tracks time for process()"() {
        def typeElems = [Stub(TypeElement)] as Set
        def roundEnv = Stub(RoundEnvironment)

        when:
        def claimed = tracker.process(typeElems, roundEnv)

        then:
        1 * delegate.process(typeElems, roundEnv) >> {
            simulateWorkWithDuration(42)
            true
        }
        claimed
        result.executionTimeInMillis == 42

        when:
        tracker.process(typeElems, roundEnv)

        then:
        1 * delegate.process(typeElems, roundEnv) >> {
            simulateWorkWithDuration(23)
            true
        }
        result.executionTimeInMillis == 42 + 23
    }

    def "tracks time for getCompletions()"() {
        def element = Stub(Element)
        def annotation = Stub(AnnotationMirror)
        def member = Stub(ExecutableElement)
        def userText = "foo"
        def completion = Completions.of("bar")

        when:
        def completions = tracker.getCompletions(element, annotation, member, userText)

        then:
        1 * delegate.getCompletions(element, annotation, member, userText) >> {
            simulateWorkWithDuration(11)
            [completion] as Set
        }
        completions == [completion] as Set
        result.executionTimeInMillis == 11
    }

    def "tracks time in nanoseconds"() {
        given:
        delegate.init(_) >> {
            simulateWorkWithDuration(500_000, NANOSECONDS)
        }

        when:
        tracker.init(null)

        then:
        result.executionTimeInMillis == 0

        when:
        tracker.init(null)

        then:
        result.executionTimeInMillis == 1
    }

    def simulateWorkWithDuration(long duration, TimeUnit unit = MILLISECONDS) {
        currentNanos += NANOSECONDS.convert(duration, unit)
    }
}
