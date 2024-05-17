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

import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor

import static org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType.AGGREGATING
import static org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType.ISOLATING
import static org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType.UNKNOWN

class DynamicProcessorTest extends Specification {

    Processor delegate = Stub(Processor)
    AnnotationProcessorResult result = new AnnotationProcessorResult(null, "")
    DynamicProcessor processor = new DynamicProcessor(delegate, result)

    def "sets initial processor type"() {
        expect:
        result.type == UNKNOWN
    }

    def "updates processor type from options for #type processor"() {
        given:
        delegate.getSupportedOptions() >> [type.processorOption]
        when:
        processor.init(Stub(ProcessingEnvironment))
        then:
        result.type == type
        where:
        type << [AGGREGATING, ISOLATING]
    }
}
