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
import org.gradle.api.attributes.CompileView
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

/**
 * Tests attribute matching against configurations and their variants in the context of the JVM ecosystem.
 * Here, we apply the JVM ecosystem compatibility and disambiguation rules and verify that given a set of
 * requested attributes, the proper variant is selected.
 * <p>
 * This test aims to mirror the actual variant selection process within Gradle, specifically when one project
 * is resolving their own configurations which in turn depend on the consumable configurations of other Gradle
 * projects. This test does not attempt to model or test the interactions with published artifacts.
 */
class JavaEcosystemAttributeMatcherTest extends Specification {

    def matcher = new ComponentAttributeMatcher()
    def schema = new DefaultAttributesSchema(matcher, TestUtil.instantiatorFactory(), new TestIsolatableFactory())
    AttributeSelectionSchema selectionSchema
    def explanationBuilder = Stub(AttributeMatchingExplanationBuilder)

    def setup() {
        JavaEcosystemSupport.configureSchema(schema, TestUtil.objectFactory())
        selectionSchema = schema.mergeWith(EmptySchema.INSTANCE)
    }

    def "resolve compileClasspath with java plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements[0]
    }

    def "resolve compileClasspath with java-library plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements[1]
    }

    def "resolve compileClasspath for internal compile view with java plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_INTERNAL)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
            apiElements,
            compileElements,
            runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == compileElements[0]
    }

    def "resolve compileClasspath for internal compile view with java-library plugin"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_INTERNAL)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
            apiElements,
            compileElements,
            runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == compileElements[0]
    }

    def "resolve runtimeClasspath with java plugin"() {
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == runtimeElements[0]
    }

    def "resolve runtimeClasspath with java-library plugin"() {
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == runtimeElements[0]
    }

    def "resolve runtimeClasspath for internal compile view with java plugin"() {
        // Even if we request the internal compile view during compile-time, we still want runtimeElements during runtime
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8, CompileView.JAVA_API)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
            apiElements,
            compileElements,
            runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == runtimeElements[0]
    }

    def "resolve runtimeClasspath for internal compile view with java-library plugin"() {
        // Even if we request the internal compile view during compile-time, we still want runtimeElements during runtime
        def requested = attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8, CompileView.JAVA_API)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        when:
        def candidates = [
            apiElements,
            compileElements,
            runtimeElements,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == runtimeElements[0]
    }

    def "resolve compileClasspath with java plugin targetJvm={8,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        def apiElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_API)]

        def compileElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_INTERNAL)]
        def runtimeElements11 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11),
                                 attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
                apiElements11,
                compileElements11,
                runtimeElements11,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements[0]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        def apiElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_API),
                             attributes(Usage.JAVA_API, LibraryElements.CLASSES, 11, CompileView.JAVA_API)]
        def compileElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_INTERNAL)]
        def runtimeElements11 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11),
                                 attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
                apiElements11,
                compileElements11,
                runtimeElements11,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements[1]
    }

    def "resolve compileClasspath with java plugin targetJvm={8,9,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]

        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        def apiElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_API)]

        def compileElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_INTERNAL)]
        def runtimeElements9 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9),
                                attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 9)]

        def apiElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_API)]

        def compileElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_INTERNAL)]
        def runtimeElements11 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11),
                                 attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)]

        when:
        def candidates = [
                apiElements,

                compileElements,
                runtimeElements,
                apiElements9,

                compileElements9,
                runtimeElements9,
                apiElements11,

                compileElements11,
                runtimeElements11,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements9[0]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,9,11} requesting 9"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API),
                           attributes(Usage.JAVA_API, LibraryElements.CLASSES, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8),
                               attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 8)]

        def apiElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_API),
                            attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9, CompileView.JAVA_API)]
        def compileElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_INTERNAL)]
        def runtimeElements9 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9),
                                attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 9)]

        def apiElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_API),
                             attributes(Usage.JAVA_API, LibraryElements.CLASSES, 11, CompileView.JAVA_API)]
        def compileElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_INTERNAL)]
        def runtimeElements11 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11),
                                 attributes(Usage.JAVA_RUNTIME, LibraryElements.CLASSES, 11)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
                apiElements9,
                compileElements9,
                runtimeElements9,
                apiElements11,
                compileElements11,
                runtimeElements11,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements9[1]
    }

    def "resolve compileClasspath with java-library plugin targetJvm={8,9,11} requesting 9 only primary"() {
        def requested = attributes(Usage.JAVA_API, LibraryElements.CLASSES, 9)

        def apiElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_API)]
        def compileElements = [attributes(Usage.JAVA_API, LibraryElements.JAR, 8, CompileView.JAVA_INTERNAL)]
        def runtimeElements = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 8)]

        def apiElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_API)]
        def compileElements9 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 9, CompileView.JAVA_INTERNAL)]
        def runtimeElements9 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 9)]

        def apiElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_API)]
        def compileElements11 = [attributes(Usage.JAVA_API, LibraryElements.JAR, 11, CompileView.JAVA_INTERNAL)]
        def runtimeElements11 = [attributes(Usage.JAVA_RUNTIME, LibraryElements.JAR, 11)]

        when:
        def candidates = [
                apiElements,
                compileElements,
                runtimeElements,
                apiElements9,
                compileElements9,
                runtimeElements9,
                apiElements11,
                compileElements11,
                runtimeElements11,
        ]
        def matches = matchConfigurations(candidates, requested)
        then:
        matches == apiElements9[0]
    }

    /**
     * Gradle's process of attribute matching against configurations and their variants consists of two steps.
     * First, attribute matching is performed against the implicit variants of each consumable configuration.
     * Then, assuming attribute matching returns a single matched configuration, Gradle will then perform
     * attribute matching against each sub-variant (including the implicit) of the matched configuration.
     * <p>
     * This method emulates that process. The given {@code candidates} is a nested array of attribute
     * containers. Each inner array represents a configuration. The first element of each inner array
     * represents the attributes of the implicit variant. Any further elements of the inner array are explicit
     * sub-variants. The outer array is the collection of all consumable configurations being tested.
     *
     * @param candidates All configurations and their variants being matched against.
     * @param requested The requested attributes.
     *
     * @return The single matched variant.
     *
     * @throws AssertionError If the first round of attribute matching failed to match a single configuration
     *      or the second round failed to match a single variant.
     */
    def matchConfigurations(List<List<AttributeContainerInternal>> candidates, AttributeContainerInternal requested) {
        // The first element in each configuration array is the implicit variant.
        def implicitVariants = candidates.collect { it.first() }
        def configurationMatches = matcher.match(selectionSchema, implicitVariants, requested, null, explanationBuilder)

        // This test is checking only for successful (single) matches. If we matched multiple configurations
        // in the first round, something is wrong here. Fail before attempting the second round of variant matching.
        assert configurationMatches.size() == 1

        // Get all the variants for the configuration which was selected and apply variant matching on them.
        def configurationVariants = candidates.get(implicitVariants.indexOf(configurationMatches.get(0)))
        def variantMatches = matcher.match(selectionSchema, configurationVariants, requested, null, explanationBuilder)

        // Once again, the purpose of this test is for successful results. Something is wrong if we have
        // multiple matched variants.
        assert variantMatches.size() == 1
        return variantMatches[0]
    }

    private static AttributeContainerInternal attributes(String usage, String libraryElements, Integer targetJvm, String compileView = null) {
        Map<Attribute<Object>, Object> attrs = [
            (Usage.USAGE_ATTRIBUTE): AttributeTestUtil.named(Usage, usage),
            (TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE): targetJvm,
            (LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE): AttributeTestUtil.named(LibraryElements, libraryElements)] +
            (compileView != null ? [(CompileView.VIEW_ATTRIBUTE): AttributeTestUtil.named(CompileView, compileView)] : [:])
        return AttributeTestUtil.attributesTyped(attrs)
    }
}
