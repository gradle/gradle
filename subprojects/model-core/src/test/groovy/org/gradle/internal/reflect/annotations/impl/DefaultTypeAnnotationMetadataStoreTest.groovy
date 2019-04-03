/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect.annotations.impl

import groovy.transform.PackageScope
import org.gradle.api.file.FileCollection
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.reflect.ParameterValidationContext
import org.gradle.internal.reflect.annotations.PropertyAnnotationCategory
import spock.lang.Issue
import spock.lang.Specification

import javax.annotation.Nullable
import java.lang.annotation.Annotation
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import static org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStoreTest.Category.BIG
import static org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStoreTest.Category.SMALL

class DefaultTypeAnnotationMetadataStoreTest extends Specification {
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()

    def "finds not-annotated properties"() {
        expect:
        assertProperties TypeWithNotAnnotatedProperty, [
            publicProperty: [:],
            protectedProperty: [:],
            packageProperty: [:]
            // Note that we don't find un-annotated private getters
        ]
    }

        @SuppressWarnings("unused")
        class TypeWithNotAnnotatedProperty {
            @IrrelevantAnnotation
            String getPublicProperty() { "public" }

            @IrrelevantAnnotation
            protected String getProtectedProperty() { "protected" }

            @IrrelevantAnnotation
            @PackageScope String getPackageProperty() { "default" }

            @IrrelevantAnnotation
            private String getPrivateProperty() { "private" }
        }

    def "finds annotated properties"() {
        expect:
        assertProperties TypeWithAnnotatedProperty, [
            publicProperty: [(SMALL): SmallAnnotation],
            protectedProperty: [(SMALL): SmallAnnotation],
            packageProperty: [(SMALL): SmallAnnotation],
            privateProperty: [(SMALL): SmallAnnotation]
        ], [
            "Property 'privateProperty' is private and annotated with @$SmallAnnotation.simpleName"
        ]
    }

        @SuppressWarnings("unused")
        class TypeWithAnnotatedProperty {
            @SmallAnnotation
            String getPublicProperty() { "public" }

            @SmallAnnotation
            protected String getProtectedProperty() { "protected" }

            @SmallAnnotation
            @PackageScope String getPackageProperty() { "default" }

            @SmallAnnotation
            private String getPrivateProperty() { "private" }
        }

    def "finds annotation on field"() {
        expect:
        assertProperties TypeWithFieldAnnotation, [
            property: [(SMALL): SmallAnnotation],
        ]
    }

        @SuppressWarnings("unused")
        class TypeWithFieldAnnotation {
            @SmallAnnotation
            private final String property = "test"

            String getProperty() { property }
        }

    def "superclass properties are present in subclass"() {
        expect:
        assertProperties TypeWithSuperclassProperties, [
            baseProperty: [(SMALL): SmallAnnotation],
            subclassProperty: [(SMALL): SmallAnnotation]
        ]
    }

        @SuppressWarnings("unused")
        interface BaseTypeWithSuperClassProperites {
            @SmallAnnotation
            String getBaseProperty()
        }

        @SuppressWarnings("unused")
        interface TypeWithSuperclassProperties extends BaseTypeWithSuperClassProperites {
            @SmallAnnotation
            String getSubclassProperty()
        }

    def "overridden properties inherit super-class annotations"() {
        expect:
        assertProperties TypeWithInheritedProperty, [
            overriddenProperty: [(SMALL): SmallAnnotation]
        ]
    }

        @SuppressWarnings("unused")
        class BaseTypeWithInheritedProperty {
            @SmallAnnotation("super-class")
            String getOverriddenProperty() { "test" }
        }

        @SuppressWarnings("unused")
        class TypeWithInheritedProperty extends BaseTypeWithInheritedProperty {
            @Override
            String getOverriddenProperty() { "test" }
        }

    def "overridden properties inherit super-class annotations when same annotation is present in interfaces"() {
        expect:
        assertProperties TypeWithInheritedPropertyFromSuperClassAndInterface, [
            overriddenProperty: [(SMALL): { it instanceof SmallAnnotation && it.value() == "super-class" }]
        ]
    }

        @SuppressWarnings("unused")
        interface InterfaceWithInheritedProperty {
            @SmallAnnotation("interface")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        class TypeWithInheritedPropertyFromSuperClassAndInterface
            extends BaseTypeWithInheritedProperty
            implements InterfaceWithInheritedProperty
        {
            @Override
            String getOverriddenProperty() { "test" }
        }

    def "implemented properties inherit annotation from first interface"() {
        expect:
        assertProperties TypeWithImplementedPropertyFromInterfaces, [
            overriddenProperty: [(SMALL): { it instanceof SmallAnnotation && it.value() == "first-interface" }]
        ]
    }

        @SuppressWarnings("unused")
        interface FirstInterfaceWithInheritedProperty {
            @SmallAnnotation("first-interface")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        interface SecondInterfaceWithInheritedProperty {
            @SmallAnnotation("second-interface")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        class TypeWithImplementedPropertyFromInterfaces
            implements FirstInterfaceWithInheritedProperty, SecondInterfaceWithInheritedProperty
        {
            @Override
            String getOverriddenProperty() { "test" }
        }

    def "can override same annotation in subclass"() {
        expect:
        assertProperties TypeWithOverride, [
            overriddenProperty: [(SMALL): { it instanceof SmallAnnotation && it.value() == "override" }]
        ]
    }

        @SuppressWarnings("unused")
        interface BaseTypeWithOverride {
            @SmallAnnotation("base")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        interface TypeWithOverride extends BaseTypeWithOverride {
            @SmallAnnotation("override")
            String getOverriddenProperty()
        }

    def "can override annotation with different annotation in same category in subclass"() {
        expect:
        assertProperties CanOverrideOverrideCategoryClass, [
            overriddenProperty: [(BIG): BigAnnotation2, (SMALL): SmallAnnotation]
        ]
    }

        @SuppressWarnings("unused")
        interface CanOverrideCategoryBaseClass {
            @BigAnnotation1
            String getOverriddenProperty()
        }

        interface CanOverrideCategoryIntermediateClass extends CanOverrideCategoryBaseClass {
            @Override
            @SmallAnnotation
            String getOverriddenProperty()
        }

        interface CanOverrideOverrideCategoryClass extends CanOverrideCategoryIntermediateClass {
            @Override
            @BigAnnotation2
            String getOverriddenProperty()
        }

    def "uses first declared annotation of conflicting category and warns about the conflict"() {
        expect:
        assertProperties TypeWithPropertiesWithMultipleAnnotationsOfSameCategory, [
            doubleAnnotatedProperty1then2: [(BIG): BigAnnotation1],
            doubleAnnotatedProperty2then1: [(BIG): BigAnnotation2]
        ], [
            "Property 'doubleAnnotatedProperty1then2' has multiple big annotations: @BigAnnotation1, @BigAnnotation2",
            "Property 'doubleAnnotatedProperty2then1' has multiple big annotations: @BigAnnotation1, @BigAnnotation2"
        ]
    }

        @SuppressWarnings("unused")
        interface TypeWithPropertiesWithMultipleAnnotationsOfSameCategory {
            @BigAnnotation1
            @BigAnnotation2
            String getDoubleAnnotatedProperty1then2()

            @BigAnnotation2
            @BigAnnotation1
            String getDoubleAnnotatedProperty2then1()
        }

    def "warns about both method and field having the same annotation, prefers method annotation"() {
        expect:
        assertProperties WithBothFieldAndGetterAnnotation, [
            inputFiles: [(SMALL): { it instanceof SmallAnnotation && it.value() == "method" }]
        ], [
            "Property 'inputFiles' has both a getter and field declared with annotation @SmallAnnotation"
        ]
    }

        @SuppressWarnings("unused")
        class WithBothFieldAndGetterAnnotation {
            @SmallAnnotation("field")
            FileCollection inputFiles

            @SmallAnnotation("method")
            FileCollection getInputFiles() {
                return inputFiles
            }
        }

    def "doesn't warn about both method and field having the same irrelevant annotation"() {
        expect:
        assertProperties WithBothFieldAndGetterAnnotationButIrrelevant, [
            inputFiles: [(SMALL): SmallAnnotation]
        ]
    }

        @SuppressWarnings("unused")
        class WithBothFieldAndGetterAnnotationButIrrelevant {
            @IrrelevantAnnotation
            FileCollection inputFiles

            @IrrelevantAnnotation
            @SmallAnnotation
            FileCollection getInputFiles() {
                return inputFiles
            }
        }

    def "warns about annotations on private properties"() {
        expect:
        assertProperties WithAnnotationsOnPrivateProperty, [
            privateInput: [(BIG): BigAnnotation1]
        ], [
            "Property 'privateInput' is private and annotated with @${BigAnnotation1.simpleName}"
        ]
    }

        @SuppressWarnings("unused")
        class WithAnnotationsOnPrivateProperty {
            @BigAnnotation1
            private String getPrivateInput() {
                'Input'
            }

            private String getNotAnInput() {
                'Not an input'
            }
        }



    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private field is recognized for is-getter"() {
        expect:
        assertProperties IsGetterType, [
            feature1: [(SMALL): SmallAnnotation],
            feature2: [:]
        ]
    }

        @SuppressWarnings("GroovyUnusedDeclaration")
        private static class IsGetterType {
            @SmallAnnotation
            private boolean feature1
            private boolean feature2

            boolean isFeature1() {
                return feature1
            }
            void setFeature1(boolean enabled) {
                this.feature1 = enabled
            }
            boolean isFeature2() {
                return feature2
            }
            void setFeature2(boolean enabled) {
                this.feature2 = enabled
            }
        }

    void assertProperties(Class<?> type, Map<String, Map<PropertyAnnotationCategory, ?>> expectedProperties, List<String> expectedErrors = []) {
        def metadata = createStore().getTypeMetadata(type)
        def actualPropertyNames = metadata.propertiesAnnotationMetadata*.propertyName.sort()
        def expectedPropertyNames = expectedProperties.keySet().sort()
        assert actualPropertyNames == expectedPropertyNames

        metadata.propertiesAnnotationMetadata.forEach { actualProperty ->
            def expectedAnnotations = expectedProperties[actualProperty.propertyName]

            def actualCategories = actualProperty.annotations.keySet().sort()
            def expectedCategories = expectedAnnotations.keySet().sort()
            assert actualCategories == expectedCategories

            actualCategories.forEach { category ->
                def actualAnnotation = actualProperty.annotations[category]
                def expectedAnnotation = expectedAnnotations[category]
                if (expectedAnnotation instanceof Class) {
                    assert actualAnnotation.annotationType() == expectedAnnotation
                } else if (expectedAnnotation instanceof Closure) {
                    assert expectedAnnotation.call(actualAnnotation)
                } else {
                    throw new IllegalArgumentException("Unknown expectation $expectedAnnotation")
                }
            }
        }

        List<String> actualErrors = []
        def visitor = new ParameterValidationContext() {
            @Override
            void visitError(@Nullable String ownerPath, String propertyName, String message) {
                actualErrors.add("Property '$propertyName' $message")
            }

            @Override
            void visitError(String message) {
                actualErrors.add(message)
            }

            @Override
            void visitErrorStrict(@Nullable String ownerPath, String propertyName, String message) {
                actualErrors.add("Property '$propertyName' $message [STRICT]")
            }

            @Override
            void visitErrorStrict(String message) {
                actualErrors.add("$message  [STRICT]")
            }
        }
        metadata.visitValidationFailures("owner", visitor)
        actualErrors.sort()
        expectedErrors.sort()
        assert actualErrors == expectedErrors
    }

    def createStore(Map<Class<? extends Annotation>, PropertyAnnotationCategory> annotationCategories = [
        (BigAnnotation1): BIG,
        (BigAnnotation2): BIG,
        (SmallAnnotation): SMALL,
    ]) {
        new DefaultTypeAnnotationMetadataStore(annotationCategories, [Object, GroovyObject] as Set, [Object, GroovyObject] as List, cacheFactory)
    }

    enum Category implements PropertyAnnotationCategory {
        BIG, SMALL;

        @Override
        String getDisplayName() {
            return name().toLowerCase()
        }
    }

    enum ErrorSeverity {
        ERROR, STRICT_ERROR
    }
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface BigAnnotation1 {
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface BigAnnotation2 {
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface SmallAnnotation {
    String value() default "one"
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface IrrelevantAnnotation {
}
