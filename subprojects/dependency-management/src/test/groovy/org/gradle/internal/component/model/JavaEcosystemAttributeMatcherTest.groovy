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

    def "resolve compileClasspath with java plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)

        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        when:
        def candidates = [
                apiElements,
                runtimeElements,
                runtimeElementsClasses
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElements ]
    }

    def "resolve compileClasspath with java-library plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)
        def apiElementsClasses = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)
        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        when:
        def candidates = [
                apiElements,
                apiElementsClasses,
                runtimeElements,
                runtimeElementsClasses
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElementsClasses ]
    }

    def "resolve runtimeClasspath with java plugin"() {
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)

        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        when:
        def candidates = [
                apiElements,
                runtimeElements,
                runtimeElementsClasses
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ runtimeElements ]
    }

    def "resolve runtimeClasspath with java-library plugin"() {
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)
        def apiElementsClasses = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)
        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        when:
        def candidates = [
                apiElements,
                apiElementsClasses,
                runtimeElements,
                runtimeElementsClasses
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ runtimeElements ]
    }

    def "resolve compileClasspath with java plugin targetJvm={8,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)

        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        def apiElements11 = attributes(Usage.JAVA_API, LibraryElements.JAR, 11)

        def runtimeElements11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)
        def runtimeElementsClasses11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)

        when:
        def candidates = [
                apiElements,
                runtimeElements,
                runtimeElementsClasses,
                apiElements11,
                runtimeElements11,
                runtimeElementsClasses11,
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElements ]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)
        def apiElementsClasses = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)
        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        def apiElements11 = attributes(Usage.JAVA_API, LibraryElements.JAR, 11)
        def apiElementsClasses11 = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 11)
        def runtimeElements11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)
        def runtimeElementsClasses11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)

        when:
        def candidates = [
                apiElements,
                apiElementsClasses,
                runtimeElements,
                runtimeElementsClasses,
                apiElements11,
                apiElementsClasses11,
                runtimeElements11,
                runtimeElementsClasses11,
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElementsClasses ]
    }

    def "resolve compileClasspath with java plugin targetJvm={8,9,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)

        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        def apiElements9 = attributes(Usage.JAVA_API, LibraryElements.JAR, 9)

        def runtimeElements9 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9)
        def runtimeElementsClasses9 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 9)

        def apiElements11 = attributes(Usage.JAVA_API, LibraryElements.JAR, 11)

        def runtimeElements11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)
        def runtimeElementsClasses11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)

        when:
        def candidates = [
                apiElements,

                runtimeElements,
                runtimeElementsClasses,
                apiElements9,

                runtimeElements9,
                runtimeElementsClasses9,
                apiElements11,

                runtimeElements11,
                runtimeElementsClasses11,
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElements9 ]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,9,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)
        def apiElementsClasses = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)
        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)
        def runtimeElementsClasses = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)

        def apiElements9 = attributes(Usage.JAVA_API, LibraryElements.JAR, 9)
        def apiElementsClasses9 = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)
        def runtimeElements9 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9)
        def runtimeElementsClasses9 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 9)

        def apiElements11 = attributes(Usage.JAVA_API, LibraryElements.JAR, 11)
        def apiElementsClasses11 = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 11)
        def runtimeElements11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)
        def runtimeElementsClasses11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)

        when:
        def candidates = [
                apiElements,
                apiElementsClasses,
                runtimeElements,
                runtimeElementsClasses,
                apiElements9,
                apiElementsClasses9,
                runtimeElements9,
                runtimeElementsClasses9,
                apiElements11,
                apiElementsClasses11,
                runtimeElements11,
                runtimeElementsClasses11,
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElementsClasses9 ]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,9,11} requesting 9 only primary"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = attributes(Usage.JAVA_API, LibraryElements.JAR, 8)
        def runtimeElements = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)

        def apiElements9 = attributes(Usage.JAVA_API, LibraryElements.JAR, 9)
        def runtimeElements9 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9)

        def apiElements11 = attributes(Usage.JAVA_API, LibraryElements.JAR, 11)
        def runtimeElements11 = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)

        when:
        def candidates = [
                apiElements,
                runtimeElements,
                apiElements9,
                runtimeElements9,
                apiElements11,
                runtimeElements11,
        ]
        def matches = matcher.match(selectionSchema, candidates, requested, null, explanationBuilder)
        then:
        matches == [ apiElements9 ]
    }

    private static AttributeContainerInternal attributes(String usage, String libraryElements, Integer targetJvm) {
        return AttributeTestUtil.attributesTyped(
                (Usage.USAGE_ATTRIBUTE): AttributeTestUtil.named(Usage, usage),
                (TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE): targetJvm,
                (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE): AttributeTestUtil.named(LibraryElements, libraryElements)
        )
    }
}
