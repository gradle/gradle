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

import com.google.common.collect.HashMultimap
import com.google.gson.Gson
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblemReporter
import org.gradle.api.problems.internal.DocLink
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.api.problems.internal.ProblemEmitter
import spock.lang.Specification

class ValidationProblemSerializationTest extends Specification {

    Gson gson = ValidationProblemSerialization.createGsonBuilder().create()
    InternalProblemReporter problemReporter = new DefaultProblemReporter(Stub(ProblemEmitter), [], org.gradle.internal.operations.CurrentBuildOperationRef.instance(), HashMultimap.create())

    def "can serialize and deserialize a validation problem"() {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].definition.id.group.name == "validation"
        deserialized[0].definition.id.group.displayName == "Validation"
        deserialized[0].definition.id.group.parent == null

        deserialized[0].locations.isEmpty()
        deserialized[0].definition.documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a location"() {
        given:
        def problem = problemReporter.create {
            it.id("type", "label", GradleCoreProblemGroup.validation())
                .lineInFileLocation("location", 1, 2, 3)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "type"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations[0].path == "location"
        deserialized[0].locations[0].line == 1
        deserialized[0].locations[0].column == 2
        deserialized[0].locations[0].length == 3
        deserialized[0].definition.documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a documentation link"() {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
                .documentedAt(new TestDocLink())
                .lineInFileLocation("location", 1, 1)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations[0].path == "location"
        deserialized[0].locations[0].line == 1
        deserialized[0].locations[0].column == 1
        deserialized[0].definition.documentationLink.getUrl() == "url"
        deserialized[0].definition.documentationLink.getConsultDocumentationMessage() == "consult"
    }

    /**
     * Required to be a named, static class for serialization to work.
     * See https://google.github.io/gson/UserGuide.html#nested-classes-including-inner-classes
     */
    class TestDocLink implements DocLink {

        @Override
        String getUrl() {
            return "url"
        }

        @Override
        String getConsultDocumentationMessage() {
            return "consult"
        }
    }

    def "can serialize and deserialize a validation problem with a cause"() {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
                .withException(new RuntimeException("cause"))
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].exception.message == "cause"
    }

    def "can serialize and deserialize a validation problem with a severity"(Severity severity) {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
                .severity(severity)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].definition.severity == severity

        where:
        severity << Severity.values()
    }

    def "can serialize and deserialize a validation problem with a solution"() {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
                .solution("solution 0")
                .solution("solution 1")
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].solutions[0] == "solution 0"
        deserialized[0].solutions[1] == "solution 1"
    }

    def "can serialize and deserialize a validation problem with additional data"() {
        given:
        def problem = problemReporter.create {
            it.id("id", "label", GradleCoreProblemGroup.validation())
                .additionalData("key 1", "value 1")
                .additionalData("key 2", "value 2")
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].locations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].additionalData["key 1"] == "value 1"
        deserialized[0].additionalData["key 2"] == "value 2"
    }
}
