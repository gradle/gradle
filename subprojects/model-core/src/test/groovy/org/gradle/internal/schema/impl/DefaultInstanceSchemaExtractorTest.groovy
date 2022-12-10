/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.schema.impl

import groovy.transform.MapConstructor
import org.gradle.internal.reflect.annotations.Long
import org.gradle.internal.reflect.annotations.Short
import org.gradle.internal.reflect.annotations.TestAnnotationHandlingSupport
import org.gradle.internal.reflect.annotations.TestNested
import org.gradle.internal.reflect.annotations.ThisIsAThing
import org.gradle.internal.reflect.annotations.Tint
import org.gradle.internal.reflect.validation.TypeValidationContext
import spock.lang.Specification

class DefaultInstanceSchemaExtractorTest extends Specification implements TestAnnotationHandlingSupport {

    def "can extract empty schema"() {
        when:
        def schema = instanceSchemaExtractor.extractSchema(new Object(), Mock(TypeValidationContext))

        then:
        schema.testProperties().collect().empty
        0 * _
    }

    @MapConstructor
    class TypeWithSimpleProperties {
        @Short
        @Tint("blue")
        String name

        @Long
        String longName
    }

    def "can extract simple properties"() {
        def thing = new TypeWithSimpleProperties(name: "lajos", longName: "Nagy Lajos")
        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))

        then:
        schema.testProperties()*.qualifiedName ==~ ["name", "longName"]
        0 * _
    }

    @MapConstructor
    class TypeWithNestedProperties {
        @TestNested
        TypeWithSimpleProperties nested
    }

    def "can extract nested properties"() {
        def thing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "lajos", longName: "Nagy Lajos")
        )
        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))

        then:
        schema.nestedProperties()*.qualifiedName ==~ ["nested"]
        schema.testProperties()*.qualifiedName ==~ ["nested.name", "nested.longName"]
        0 * _
    }

    def "schema equals itself"() {
        def thing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "lajos", longName: "Nagy Lajos")
        )

        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))

        then:
        schema == schema
        0 * _
    }

    def "schema equals re-extracted schema"() {
        def thing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "lajos", longName: "Nagy Lajos")
        )

        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))
        def reExtractedSchema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))

        then:
        schema == reExtractedSchema
        0 * _
    }

    def "schema differs for different instances"() {
        def thing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "lajos", longName: "Nagy Lajos")
        )
        def otherThing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "tibor", longName: "Nagy Lajos")
        )

        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, Mock(TypeValidationContext))
        def otherSchema = instanceSchemaExtractor.extractSchema(otherThing, Mock(TypeValidationContext))

        then:
        schema != otherSchema
        0 * _
    }

    @MapConstructor
    @ThisIsAThing(invalid = true)
    class InvalidTypeWithUnannotatedNestedProperties {
        @TestNested
        TypeWithUnannotatedProperty nested
    }

    class TypeWithUnannotatedProperty {
        String name
    }

    def "detects validation errors"() {
        def thing = new InvalidTypeWithUnannotatedNestedProperties(
            nested: new TypeWithUnannotatedProperty()
        )
        def validationContext = Mock(TypeValidationContext)
        when:
        def schema = instanceSchemaExtractor.extractSchema(thing, validationContext)

        then:
        // TODO Check this more precisely
        1 * validationContext.visitTypeProblem(_)
        1 * validationContext.visitPropertyProblem(_)

        then:
        schema.nestedProperties()*.qualifiedName ==~ ["nested"]
        schema.testProperties()*.qualifiedName ==~ []
    }
}
