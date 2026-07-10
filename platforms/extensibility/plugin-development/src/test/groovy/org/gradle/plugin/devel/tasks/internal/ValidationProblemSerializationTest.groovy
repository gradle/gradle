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


import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultProblemReporter
import org.gradle.api.problems.internal.DeprecationData
import org.gradle.api.problems.internal.DeprecationDataSpec
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.GeneralData
import org.gradle.api.problems.internal.GeneralDataSpec
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.DocLinkInternal
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemReporterInternal
import org.gradle.api.problems.internal.IsolatableToBytesSerializer
import org.gradle.api.problems.internal.ProblemSummarizer
import org.gradle.api.problems.internal.ProblemsInfrastructure
import org.gradle.api.problems.internal.TypeValidationData
import org.gradle.api.problems.internal.TypeValidationDataSpec
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.reflect.Instantiator
import org.gradle.problems.buildtree.ProblemStream
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

class ValidationProblemSerializationTest extends Specification {

    def problemId = ProblemId.create("id", "label", GradleCoreProblemGroup.validation().type())

    ProblemReporterInternal problemReporter = new DefaultProblemReporter(
        Stub(ProblemSummarizer),
        CurrentBuildOperationRef.instance(),
        new ExceptionProblemRegistry(),
        null,
        new ProblemsInfrastructure(
            new AdditionalDataBuilderFactory(),
            Mock(Instantiator),
            Mock(PayloadSerializer),
            Mock(IsolatableFactory),
            Mock(IsolatableToBytesSerializer),
            Mock(ProblemStream)
        )
    )

    def "can serialize and deserialize a validation problem"(boolean asWarning) {
        given:
        def problem = problemReporter.create(problemId, {})

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.definition.id.group.name == "type-validation"
        deserialized.definition.id.group.displayName == "Gradle type validation"
        deserialized.definition.id.group.parent.name == "validation"
        deserialized.definition.id.group.parent.displayName == "Validation"
        deserialized.definition.id.group.parent.parent == null

        deserialized.originLocations.isEmpty()
        deserialized.definition.documentationLink == null

        where:
        asWarning << [false, true]
    }

    def "can serialize and deserialize a validation problem with a location"(boolean asWarning) {
        given:
        def problem = problemReporter.create(problemId) {
            it.lineInFileLocation("location", 1, 2, 3)
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.originLocations[0].path == "location"
        deserialized.originLocations[0].line == 1
        deserialized.originLocations[0].column == 2
        deserialized.originLocations[0].length == 3
        deserialized.definition.documentationLink == null

        where:
        asWarning << [false, true]
    }

    def "can serialize and deserialize a validation problem with a documentation link"(boolean asWarning) {
        given:
        def problem = problemReporter.create(problemId) {
            it.documentedAt(new TestDocLink())
                .lineInFileLocation("location", 1, 1)
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.originLocations[0].path == "location"
        deserialized.originLocations[0].line == 1
        deserialized.originLocations[0].column == 1
        deserialized.definition.documentationLink.getUrl() == "url"
        deserialized.definition.documentationLink.getConsultDocumentationMessage() == "consult"

        where:
        asWarning << [false, true]
    }

    def "can serialize and deserialize a validation problem with a cause"(boolean asWarning) {
        given:
        def problem = problemReporter.create(problemId) {
            it.withException(new RuntimeException("cause"))
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.originLocations == [] as List
        deserialized.definition.documentationLink == null
        deserialized.exception.message == "cause"

        where:
        asWarning << [false, true]
    }

    def "can serialize and deserialize a validation problem with a solution"(boolean asWarning) {
        given:
        def problem = problemReporter.create(problemId) {
            it.solution("solution 0")
                .solution("solution 1")
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.originLocations == [] as List
        deserialized.definition.documentationLink == null
        deserialized.solutions[0] == "solution 0"
        deserialized.solutions[1] == "solution 1"

        where:
        asWarning << [false, true]
    }

    def "can serialize and deserialize a validation problem with additional data"(boolean asWarning) {
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
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.originLocations == [] as List
        deserialized.definition.documentationLink == null
        (deserialized.additionalData as TypeValidationData).propertyName == 'property'
        (deserialized.additionalData as TypeValidationData).typeName == 'type'
        (deserialized.additionalData as TypeValidationData).parentPropertyName == 'parent'
        (deserialized.additionalData as TypeValidationData).pluginId == 'id'

        where:
        asWarning << [false, true]
    }

    def "can serialize generic additional data"(boolean asWarning) {
        given:
        def problem = problemReporter.internalCreate {
            it.id(problemId)
                .additionalDataInternal(GeneralDataSpec) {
                    it.put('foo', 'bar')
                }
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.additionalData instanceof GeneralData
        (deserialized.additionalData as GeneralData).asMap == ['foo' : 'bar']

        where:
        asWarning << [false, true]
    }

    def "can serialize deprecation additional data"(boolean asWarning) {
        given:
        def problem = problemReporter.internalCreate {
            it.id(problemId)
                .additionalDataInternal(DeprecationDataSpec) {
                    it.type(DeprecationData.Type.BUILD_INVOCATION)
                }
        }

        when:
        def deserialized = serializeAndDeserialize(problem, asWarning)

        then:
        deserialized.definition.id.name == "id"
        deserialized.definition.id.displayName == "label"
        deserialized.additionalData instanceof DeprecationData
        (deserialized.additionalData as DeprecationData).type == DeprecationData.Type.BUILD_INVOCATION

        where:
        asWarning << [false, true]
    }

    private static ProblemInternal serializeAndDeserialize(ProblemInternal problem, boolean asWarning) {
        def json = asWarning ? ValidationProblemSerialization.serialize([problem], []) : ValidationProblemSerialization.serialize([], [problem])
        def deserialized = ValidationProblemSerialization.deserialize(json)
        def problems = asWarning ? deserialized.warnings : deserialized.errors
        assert problems.size() == 1
        return problems[0]
    }

    /**
     * Required to be a named, static class for serialization to work.
     * See https://google.github.io/gson/UserGuide.html#nested-classes-including-inner-classes
     */
    class TestDocLink implements DocLinkInternal {

        @Override
        String getUrl() {
            return "url"
        }

        @Override
        String getConsultDocumentationMessage() {
            return "consult"
        }
    }
}
