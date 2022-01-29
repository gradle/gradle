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

package org.gradle.internal.component.model

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class JavaEcosystemAttributeMatcherTest extends Specification {

    def matcher = new ComponentAttributeMatcher()
    def schema = new DefaultAttributesSchema(matcher, TestUtil.instantiatorFactory(), new TestIsolatableFactory())
    def selectionSchema
    def explanationBuilder = Stub(AttributeMatchingExplanationBuilder)

    def setup() {
        JavaEcosystemSupport.configureSchema(schema, TestUtil.objectFactory())
        selectionSchema = schema.mergeWith(EmptySchema.INSTANCE)
    }

    def "querying for java api"() {
        def usage = Usage.USAGE_ATTRIBUTE
        def libraryElements = LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
        def targetJvm = TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE

        def requested = attributes(
                (usage): named(Usage, Usage.JAVA_API),
                (libraryElements): named(LibraryElements, LibraryElements.CLASSES),
                (targetJvm): 9,
        )

        def usageCandidates = [
            named(Usage, Usage.JAVA_API),
            named(Usage, Usage.JAVA_RUNTIME),
        ]
        def libraryElementsCandidates = [
                //named(LibraryElements, LibraryElements.CLASSES),
                named(LibraryElements, LibraryElements.JAR),
        ]
        def targetJvmCandidates = [
                8, 9, 11
        ]

        def candidates = [usageCandidates, libraryElementsCandidates, targetJvmCandidates].combinations { combination ->
            attributes((usage): combination[0], (libraryElements): combination[1], (targetJvm): combination[2])
        }

        candidates.add(attributes((usage): named(Usage, Usage.JAVA_RUNTIME), (libraryElements): named(LibraryElements, LibraryElements.CLASSES), (targetJvm): 8))
        candidates.add(attributes((usage): named(Usage, Usage.JAVA_RUNTIME), (libraryElements): named(LibraryElements, LibraryElements.CLASSES), (targetJvm): 9))
        candidates.add(attributes((usage): named(Usage, Usage.JAVA_RUNTIME), (libraryElements): named(LibraryElements, LibraryElements.CLASSES), (targetJvm): 11))

        candidates.each { println it }

        when:
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)

        println "matches"
        matches.each { println it }
        then:
        false // matches == []
    }

    private static named(Class type, String value) {
        return TestUtil.objectFactory().named(type, value)
    }

    private static AttributeContainerInternal attributes(Map<Attribute, Object> attributes) {
        def mutableAttributeContainer = AttributeTestUtil.attributesFactory().mutable()
        attributes.each { Attribute key, Object value ->
            mutableAttributeContainer.attribute(key, value)
        }
        return mutableAttributeContainer
    }

}
