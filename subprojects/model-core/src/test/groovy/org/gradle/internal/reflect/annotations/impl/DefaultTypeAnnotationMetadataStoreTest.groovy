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
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import static org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStoreTest.Category.COLOR
import static org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStoreTest.Category.SIZE

class DefaultTypeAnnotationMetadataStoreTest extends Specification {
    def store = new DefaultTypeAnnotationMetadataStore(
        [
            TestType
        ], [
            (Large): SIZE,
            (Small): SIZE,
            (Color): COLOR,
        ], [
            Object,
            GroovyObject
        ], [
            Object,
            GroovyObject
        ],
        new TestCrossBuildInMemoryCacheFactory())

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
            String getPublicProperty() { "public" }

            @Irrelevant
            protected String getProtectedProperty() { "protected" }

            @PackageScope String getPackageProperty() { "default" }

            @Irrelevant
            private String getPrivateProperty() { "private" }
        }

    def "finds annotated properties"() {
        expect:
        assertProperties TypeWithAnnotatedProperty, [
            publicProperty: [(SIZE): Large],
            protectedProperty: [(SIZE): Large],
            packageProperty: [(SIZE): Small],
            privateProperty: [(SIZE): Small],
        ], [
            "Property 'privateProperty' is private and annotated with @$Small.simpleName"
        ]
    }

        @SuppressWarnings("unused")
        class TypeWithAnnotatedProperty {
            @Large
            String getPublicProperty() { "public" }

            @Large
            protected String getProtectedProperty() { "protected" }

            @Small
            @PackageScope String getPackageProperty() { "default" }

            @Small
            private String getPrivateProperty() { "private" }
        }

    def "finds annotation on field"() {
        expect:
        assertProperties TypeWithFieldAnnotation, [
            property: [(SIZE): Large],
        ]
    }

        @SuppressWarnings("unused")
        class TypeWithFieldAnnotation {
            @Large
            private final String property = "test"

            String getProperty() { property }
        }

    def "superclass properties are present in subclass"() {
        expect:
        assertProperties TypeWithSuperclassProperties, [
            baseProperty: [(SIZE): Small],
            subclassProperty: [(SIZE): Large]
        ]
    }

        @SuppressWarnings("unused")
        interface BaseTypeWithSuperClassProperites {
            @Small
            String getBaseProperty()
        }

        @SuppressWarnings("unused")
        interface TypeWithSuperclassProperties extends BaseTypeWithSuperClassProperites {
            @Large
            String getSubclassProperty()
        }

    def "overridden properties inherit super-class annotations"() {
        expect:
        assertProperties TypeWithInheritedProperty, [
            overriddenProperty: [(COLOR): Color]
        ]
    }

        @SuppressWarnings("unused")
        class BaseTypeWithInheritedProperty {
            @Color(declaredBy = "super-class")
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
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "super-class" }]
        ]
    }

        @SuppressWarnings("unused")
        interface InterfaceWithInheritedProperty {
            @Color(declaredBy = "interface")
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
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "first-interface" }]
        ]
    }

        @SuppressWarnings("unused")
        interface FirstInterfaceWithInheritedProperty {
            @Color(declaredBy = "first-interface")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        interface SecondInterfaceWithInheritedProperty {
            @Color(declaredBy = "second-interface")
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
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "override" }]
        ]
    }

        @SuppressWarnings("unused")
        interface BaseTypeWithOverride {
            @Color(declaredBy = "base")
            String getOverriddenProperty()
        }

        @SuppressWarnings("unused")
        interface TypeWithOverride extends BaseTypeWithOverride {
            @Color(declaredBy = "override")
            String getOverriddenProperty()
        }

    def "can override annotation with different annotation in same category in subclass"() {
        expect:
        assertProperties CanOverrideOverrideCategoryClass, [
            overriddenProperty: [(SIZE): Small, (COLOR): Color]
        ]
    }

        @SuppressWarnings("unused")
        interface CanOverrideCategoryBaseClass {
            @Large
            String getOverriddenProperty()
        }

        interface CanOverrideCategoryIntermediateClass extends CanOverrideCategoryBaseClass {
            @Override
            @Color
            String getOverriddenProperty()
        }

        interface CanOverrideOverrideCategoryClass extends CanOverrideCategoryIntermediateClass {
            @Override
            @Small
            String getOverriddenProperty()
        }

    def "uses first declared annotation of conflicting category and warns about the conflict"() {
        expect:
        assertProperties TypeWithPropertiesWithMultipleAnnotationsOfSameCategory, [
            largeThenSmall: [(SIZE): Large],
            smallThenLarge: [(SIZE): Small]
        ], [
            "Property 'largeThenSmall' has multiple size annotations: @Large, @Small",
            "Property 'smallThenLarge' has multiple size annotations: @Large, @Small"
        ]
    }

        @SuppressWarnings("unused")
        interface TypeWithPropertiesWithMultipleAnnotationsOfSameCategory {
            @Large
            @Small
            String getLargeThenSmall()

            @Small
            @Large
            String getSmallThenLarge()
        }

    def "warns about both method and field having the same annotation, prefers method annotation"() {
        expect:
        assertProperties WithBothFieldAndGetterAnnotation, [
            inputFiles: [(COLOR): { it instanceof Color && it.declaredBy() == "method" }]
        ], [
            "Property 'inputFiles' has both a getter and field declared with annotation @Color"
        ]
    }

        @SuppressWarnings("unused")
        class WithBothFieldAndGetterAnnotation {
            @Color(declaredBy = "field")
            FileCollection inputFiles

            @Color(declaredBy = "method")
            FileCollection getInputFiles() {
                return inputFiles
            }
        }

    def "doesn't warn about both method and field having the same irrelevant annotation"() {
        expect:
        assertProperties WithBothFieldAndGetterAnnotationButIrrelevant, [
            inputFiles: [(COLOR): Color]
        ]
    }

        @SuppressWarnings("unused")
        class WithBothFieldAndGetterAnnotationButIrrelevant {
            @Irrelevant
            FileCollection inputFiles

            @Irrelevant
            @Color
            FileCollection getInputFiles() {
                return inputFiles
            }
        }

    def "warns about annotations on private properties"() {
        expect:
        assertProperties WithAnnotationsOnPrivateProperty, [
            privateInput: [(SIZE): Large]
        ], [
            "Property 'privateInput' is private and annotated with @${Large.simpleName}"
        ]
    }

        @SuppressWarnings("unused")
        class WithAnnotationsOnPrivateProperty {
            @Large
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
            feature1: [(SIZE): Large],
            feature2: [:]
        ]
    }

        @SuppressWarnings("GroovyUnusedDeclaration")
        private static class IsGetterType {
            @Large
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

    def "only relevant type annotations are captured"() {
        when:
        def metadata = store.getTypeMetadata(TypeWithAnnotations)

        then:
        metadata.annotations*.annotationType() == [TestType]
    }

        @TestType
        @Irrelevant
        interface TypeWithAnnotations {}

    void assertProperties(Class<?> type, Map<String, Map<PropertyAnnotationCategory, ?>> expectedProperties, List<String> expectedErrors = []) {
        def metadata = store.getTypeMetadata(type)
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

    enum Category implements PropertyAnnotationCategory {
        SIZE, COLOR;

        @Override
        String getDisplayName() {
            return name().toLowerCase()
        }
    }

    enum ErrorSeverity {
        ERROR, STRICT_ERROR
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@interface TestType {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Large {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Small {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Color {
    String declaredBy() default ""
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD, ElementType.FIELD])
@interface Irrelevant {
}
