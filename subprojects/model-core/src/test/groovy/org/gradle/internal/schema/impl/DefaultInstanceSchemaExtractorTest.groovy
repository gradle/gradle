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
import org.gradle.internal.reflect.annotations.Tint
import spock.lang.Specification

class DefaultInstanceSchemaExtractorTest extends Specification implements TestAnnotationHandlingSupport {

    def schemaExtractor = new DefaultInstanceSchemaExtractor(typeMetadataStore, TestNested)

    def "can extract empty schema"() {
        when:
        def schema = schemaExtractor.extractSchema(new Object())

        then:
        schema.properties().collect() ==~ []
    }

    @MapConstructor
    class TypeWithSimpleProperties {
        @Short @Tint("blue")
        String name

        @Long
        String longName
    }

    def "can extract simple properties"() {
        def thing = new TypeWithSimpleProperties(name: "lajos", longName: "Kovács Lajos")
        when:
        def schema = schemaExtractor.extractSchema(thing)

        then:
        schema.properties().map { it.qualifiedName }.collect() ==~ ["name", "longName"]
    }

    @MapConstructor
    class TypeWithNestedProperties {
        @TestNested
        TypeWithSimpleProperties nested
    }

    def "can extract nested properties"() {
        def thing = new TypeWithNestedProperties(
            nested: new TypeWithSimpleProperties(name: "lajos", longName: "Kovács Lajos")
        )
        when:
        def schema = schemaExtractor.extractSchema(thing)

        then:
        schema.properties().map { it.qualifiedName }.collect() ==~ ["nested", "nested.name", "nested.longName"]
    }
}
