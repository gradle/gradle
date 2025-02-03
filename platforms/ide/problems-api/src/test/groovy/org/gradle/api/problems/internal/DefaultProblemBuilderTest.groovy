/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import spock.lang.Specification

import static org.gradle.internal.problems.NoOpProblemDiagnosticsFactory.EMPTY_STREAM

class DefaultProblemBuilderTest extends Specification {

    def problemGroup = ProblemGroup.create("group", "label")
    def problemId = ProblemId.create('id', 'Problem Id', problemGroup)

    def 'additionalData accepts GeneralDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(GeneralDataSpec, spec -> {
                spec.put("key", "value")
            })
            .build().additionalData

        then:
        GeneralData.isInstance(data)
    }

    DefaultProblemBuilder createProblemBuilder() {
        new DefaultProblemBuilder(EMPTY_STREAM, new AdditionalDataBuilderFactory(), Mock(Instantiator.class), Mock(PayloadSerializer.class))
    }

    def 'additionalData accepts DeprecationDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(DeprecationDataSpec, spec -> {
                spec.type(DeprecationData.Type.USER_CODE_INDIRECT)
            })
            .build().additionalData

        then:
        DeprecationData.isInstance(data)
    }

    def 'additionalData accepts TypeValidationDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(TypeValidationDataSpec, spec -> {
                spec.propertyName("propertyName")
                spec.parentPropertyName("parentPropertyName")
                spec.pluginId("pluginId")
                spec.typeName("typeName")
            })
            .build().additionalData

        then:
        TypeValidationData.isInstance(data)
    }

    def 'additionalData accepts PropertyTraceDataInternalSpec'() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        def data = problemBuilder
            .id(problemId)
            .additionalDataInternal(PropertyTraceDataSpec, spec -> {
                spec.trace("trace")
            })
            .build().additionalData

        then:
        PropertyTraceData.isInstance(data)
    }


    def 'additionalDataInternal fails with invalid type'() {
        given:
        def problemBuilder = createProblemBuilder()


        when:
        //noinspection GroovyAssignabilityCheck
        def problem = problemBuilder
            .id(problemId)
            .additionalDataInternal(NoOpProblemDiagnosticsFactory, spec -> {
                // won't reach here

            })
            .build()
        def data = problem.additionalData

        then:
        data == null
    }

    interface InvalidAdditionalData extends AdditionalData {
        void someMethod();
    }

    def 'additionalData fails with invalid type'() {
        when:
        DefaultProblemBuilder.validateMethods(InvalidAdditionalData)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "InvalidAdditionalData must have only getters or setters using the following types: String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, or File."
    }

    // interface containing all valid types.
    // this makes sure all of these are passing the validation
    interface ValidAdditionalData extends AdditionalData {

        void setStringProperty(String value);

        void setBooleanProperty(Boolean value);

        void setCharacterProperty(Character value);

        void setByteProperty(Byte value);

        void setShortProperty(Short value);

        void setIntegerProperty(Integer value);

        void setFloatProperty(Float value);

        void setLongProperty(Long value);

        void setDoubleProperty(Double value);

        void setBigIntegerProperty(BigInteger value);

        void setBigDecimalProperty(BigDecimal value);

        void setFileProperty(File value);

        String getStringProperty();

        Boolean getBooleanProperty();

        Character getCharacterProperty();

        Byte getByteProperty();

        Short getShortProperty();

        Integer getIntegerProperty();

        Float getFloatProperty();

        Long getLongProperty();

        Double getDoubleProperty();

        BigInteger getBigIntegerProperty();

        BigDecimal getBigDecimalProperty();

        File getFileProperty();
    }

    def 'additionalData succeeds with valid type'() {
        when:
        DefaultProblemBuilder.validateMethods(ValidAdditionalData)

        then:
        noExceptionThrown()
    }

    def "can define contextual locations"() {
        given:
        def problemBuilder = createProblemBuilder()

        when:
        //noinspection GroovyAssignabilityCheck
        def problem = problemBuilder
            .id(problemId)
            .taskPathLocation(":taskPath")
            .build()


        then:
        problem.contextualLocations.every { it instanceof TaskPathLocation }
        problem.contextualLocations.collect { (it as TaskPathLocation).buildTreePath } == [':taskPath']
    }
}
