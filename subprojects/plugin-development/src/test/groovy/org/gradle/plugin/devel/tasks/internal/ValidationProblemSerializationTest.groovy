/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal

import com.google.gson.Gson
import org.gradle.api.problems.DocLink
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import spock.lang.Specification

class ValidationProblemSerializationTest extends Specification {

    Gson gson = ValidationProblemSerialization.createGsonBuilder().create()

    BuildOperationProgressEventEmitter emitter = Mock()
    Problems problems = new DefaultProblems(emitter)

    def "can serialize and deserialize a validation problem"() {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .undocumented()
                .noLocation()
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where == null
        deserialized[0].documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a location"() {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .undocumented()
                .location("location", 1, 1)
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where.path == "location"
        deserialized[0].where.line == 1
        deserialized[0].where.column == 1
        deserialized[0].documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a documentation link"() {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .documentedAt(new TestDocLink())
                .location("location", 1, 1)
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where.path == "location"
        deserialized[0].where.line == 1
        deserialized[0].where.column == 1
        deserialized[0].documentationLink.url() == "url"
        deserialized[0].documentationLink.consultDocumentationMessage() == "consult"
    }

    /**
     * Required to be a named, static class for serialization to work.
     * See https://google.github.io/gson/UserGuide.html#nested-classes-including-inner-classes
     */
    class TestDocLink implements DocLink {

        @Override
        String url() {
            return "url"
        }

        @Override
        String consultDocumentationMessage() {
            return "consult"
        }
    }

    def "can serialize and deserialize a validation problem with a cause"() {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .undocumented()
                .noLocation()
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .withException(new RuntimeException("cause"))
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where == null
        deserialized[0].documentationLink == null
        deserialized[0].cause.message == "cause"
    }

    def "can serialize and deserialize a validation problem with a severity"(Severity severity) {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .undocumented()
                .noLocation()
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .severity(severity)
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where == null
        deserialized[0].documentationLink == null
        deserialized[0].severity == severity

        where:
        severity << Severity.values()
    }

    def "can serialize and deserialize a validation problem with a solution"() {
        given:
        def problem = problems.createProblemBuilder()
                .label("label")
                .undocumented()
                .noLocation()
                .type("type")
                .group(ProblemGroup.GENERIC_ID)
                .solution("solution 0")
                .solution("solution 1")
                .build()

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].label == "label"
        deserialized[0].problemType == "type"
        deserialized[0].problemGroup.id == ProblemGroup.GENERIC_ID
        deserialized[0].where == null
        deserialized[0].documentationLink == null
        deserialized[0].solutions[0] == "solution 0"
        deserialized[0].solutions[1] == "solution 1"
    }

}
