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
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemReporter
import org.gradle.api.problems.internal.DeprecationData
import org.gradle.api.problems.internal.DeprecationDataSpec
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.GeneralData
import org.gradle.api.problems.internal.GeneralDataSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.InternalDocLink
import org.gradle.api.problems.internal.InternalProblemReporter
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.api.problems.internal.TypeValidationData
import org.gradle.api.problems.internal.TypeValidationDataSpec
import org.gradle.internal.exception.ExceptionAnalyser
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class ValidationProblemSerializationTest extends Specification {

    def problemId = ProblemId.create("id", "label", GradleCoreProblemGroup.validation().type())

    Gson gson = ValidationProblemSerialization.createGsonBuilder().create()
    InternalProblemReporter problemReporter = new DefaultProblemReporter(
        Stub(ProblemSummarizer),
        null,
        CurrentBuildOperationRef.instance(),
        new AdditionalDataBuilderFactory(),
        new ExceptionProblemRegistry(),
        Mock(ExceptionAnalyser),
        null,
        Mock(PayloadSerializer)
    )

    def "can serialize and deserialize a validation problem"() {
        given:
        def problem = problemReporter.create(problemId, {})

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].definition.id.group.name == "type-validation"
        deserialized[0].definition.id.group.displayName == "Gradle type validation"
        deserialized[0].definition.id.group.parent.name == "validation"
        deserialized[0].definition.id.group.parent.displayName == "Validation"
        deserialized[0].definition.id.group.parent.parent == null

        deserialized[0].originLocations.isEmpty()
        deserialized[0].definition.documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a location"() {
        given:
        def problem = problemReporter.create(problemId) {
            it.lineInFileLocation("location", 1, 2, 3)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations[0].path == "location"
        deserialized[0].originLocations[0].line == 1
        deserialized[0].originLocations[0].column == 2
        deserialized[0].originLocations[0].length == 3
        deserialized[0].definition.documentationLink == null
    }

    def "can serialize and deserialize a validation problem with a documentation link"() {
        given:
        def problem = problemReporter.create(problemId) {
            it.documentedAt(new TestDocLink())
                .lineInFileLocation("location", 1, 1)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations[0].path == "location"
        deserialized[0].originLocations[0].line == 1
        deserialized[0].originLocations[0].column == 1
        deserialized[0].definition.documentationLink.getUrl() == "url"
        deserialized[0].definition.documentationLink.getConsultDocumentationMessage() == "consult"
    }

    /**
     * Required to be a named, static class for serialization to work.
     * See https://google.github.io/gson/UserGuide.html#nested-classes-including-inner-classes
     */
    class TestDocLink implements InternalDocLink {

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
        def problem = problemReporter.create(problemId) {
            it.withException(new RuntimeException("cause"))
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].exception.message == "cause"
    }

    def "can serialize and deserialize a validation problem with a severity"(Severity severity) {
        given:
        def problem = problemReporter.create(problemId) {
            it.severity(severity)
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].definition.severity == severity

        where:
        severity << Severity.values()
    }

    def "can serialize and deserialize a validation problem with a solution"() {
        given:
        def problem = problemReporter.create(problemId) {
            it.solution("solution 0")
                .solution("solution 1")
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations == [] as List
        deserialized[0].definition.documentationLink == null
        deserialized[0].solutions[0] == "solution 0"
        deserialized[0].solutions[1] == "solution 1"
    }

    def "can serialize and deserialize a validation problem with additional data"() {
        given:
        def problem = problemReporter.internalCreate {
            it.id(problemId)
                .additionalDataInternal(TypeValidationDataSpec.class) {
                    it.propertyName("property")
                    it.typeName("type")
                    it.parentPropertyName("parent")
                    it.pluginId("id")
                }
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].originLocations == [] as List
        deserialized[0].definition.documentationLink == null
        (deserialized[0].additionalData as TypeValidationData).propertyName == 'property'
        (deserialized[0].additionalData as TypeValidationData).typeName == 'type'
        (deserialized[0].additionalData as TypeValidationData).parentPropertyName == 'parent'
        (deserialized[0].additionalData as TypeValidationData).pluginId == 'id'
    }

    def "can serialize generic additional data"() {
        given:
        def problem = problemReporter.internalCreate {
            it.id(problemId)
                .additionalDataInternal(GeneralDataSpec) {
                    it.put('foo', 'bar')
                }
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].additionalData instanceof GeneralData
        (deserialized[0].additionalData as GeneralData).asMap == ['foo' : 'bar']
    }

    def "can serialize deprecation additional data"() {
        given:
        def problem = problemReporter.internalCreate {
            it.id(problemId)
                .additionalDataInternal(DeprecationDataSpec) {
                    it.type(DeprecationData.Type.BUILD_INVOCATION)
                }
        }

        when:
        def json = gson.toJson([problem])
        def deserialized = ValidationProblemSerialization.parseMessageList(json)

        then:
        deserialized.size() == 1
        deserialized[0].definition.id.name == "id"
        deserialized[0].definition.id.displayName == "label"
        deserialized[0].additionalData instanceof DeprecationData
        (deserialized[0].additionalData as DeprecationData).type == DeprecationData.Type.BUILD_INVOCATION
    }
}
