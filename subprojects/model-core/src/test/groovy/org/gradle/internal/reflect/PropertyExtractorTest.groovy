/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.reflect

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileCollection
import spock.lang.Issue
import spock.lang.Specification

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class PropertyExtractorTest extends Specification {

    def extractor = new PropertyExtractor(ImmutableSet.of(PropertyType1, PropertyType2, PropertyType1Override), ImmutableSet.of(PropertyType1, PropertyType2, PropertyType1Override, SupportingAnnotation), ImmutableMultimap.of(PropertyType1, PropertyType1Override), ImmutableSet.of(Object.class, GroovyObject.class), ImmutableSet.of(Object, GroovyObject))

    class WithPropertyType1 {
        @PropertyType1 getFile() {}
    }

    class WithPropertyType2 extends WithPropertyType1 {
        @PropertyType2 @Override getFile() {}
    }

    class WithPropertyOverride extends WithPropertyType2 {
        @PropertyType1Override @Override getFile() {}
    }

    def "can override property type in subclasses"() {
        expect:
        extractor.extractPropertyMetadata(WithPropertyType1)*.propertyType == [PropertyType1]
        extractor.extractPropertyMetadata(WithPropertyType2)*.propertyType == [PropertyType2]
        extractor.extractPropertyMetadata(WithPropertyOverride)*.propertyType == [PropertyType1Override]
    }

    class OverridingProperties {
        @PropertyType1 @PropertyType1Override FileCollection inputFiles1
        @PropertyType1Override @PropertyType1 FileCollection inputFiles2
    }

    def "overriding annotation on same property takes effect"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(OverridingProperties)

        then:
        assertPropertyTypes(typeMetadata, inputFiles1: PropertyType1Override, inputFiles2: PropertyType1Override)
        typeMetadata*.validationMessages.flatten().empty
    }

    class BasePropertyType1OverrideProperty {
        @PropertyType1Override FileCollection overriddenType1Override
        @PropertyType1 FileCollection overriddenType1
    }

    class OverridingPropertyType1Property extends BasePropertyType1OverrideProperty {
        @PropertyType1
        @Override
        FileCollection getOverriddenType1Override() {
            return super.getOverriddenType1Override()
        }

        @PropertyType1Override
        @Override
        FileCollection getOverriddenType1() {
            return super.getOverriddenType1()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/913")
    def "overriding annotation does not take precedence in sub-type"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(OverridingPropertyType1Property)

        then:
        assertPropertyTypes(typeMetadata, overriddenType1Override: PropertyType1, overriddenType1: PropertyType1Override)
        typeMetadata*.validationMessages.flatten().empty
    }

    class WithBothFieldAndGetterAnnotation {
        @PropertyType1 FileCollection inputFiles

        @PropertyType1
        FileCollection getInputFiles() {
            return inputFiles
        }
    }

    def "warns about both method and field having the same annotation"() {
        when:
        def metadata = extractor.extractPropertyMetadata(WithBothFieldAndGetterAnnotation)

        then:
        assertPropertyTypes(metadata, inputFiles: PropertyType1)
        metadata*.validationMessages.flatten() == ["has both a getter and field declared with annotation @${PropertyType1.simpleName}"]
    }

    class WithBothFieldAndGetterAnnotationButIrrelevant {
        @IrrelevantAnnotation FileCollection inputFiles

        @IrrelevantAnnotation @PropertyType1
        FileCollection getInputFiles() {
            return inputFiles
        }
    }

    def "doesn't warn about both method and field having the same irrelevant annotation"() {
        when:
        def metadata = extractor.extractPropertyMetadata(WithBothFieldAndGetterAnnotationButIrrelevant)

        then:
        assertPropertyTypes(metadata, inputFiles: PropertyType1)
        metadata*.validationMessages.flatten().empty
    }

    class WithAnnotationsOnPrivateProperties {
        @PropertyType1
        private String getInput() {
            'Input'
        }

        @PropertyType2
        private File getOutputFile() {
            null
        }

        private String getNotAnInput() {
            'Not an input'
        }
    }

    def "warns about annotations on private properties"() {
        when:
        def metadata = extractor.extractPropertyMetadata(WithAnnotationsOnPrivateProperties)

        then:
        assertPropertyTypes(metadata, input: PropertyType1, outputFile: PropertyType2, notAnInput: null)
        metadata*.validationMessages.flatten() as List == [
            "is private and annotated with an input or output annotation",
            "is private and annotated with an input or output annotation",
        ]
    }

    class WithConflictingPropertyTypes {
        @PropertyType1
        @PropertyType2
        File inputThing

        @PropertyType2
        @PropertyType1
        File confusedFile
    }

    def "warns about conflicting property types being specified"() {
        when:
        def metadata = extractor.extractPropertyMetadata(WithConflictingPropertyTypes)

        then:
        assertPropertyTypes(metadata, inputThing: PropertyType1, confusedFile: PropertyType2)
        metadata*.validationMessages.flatten() as Set == [
            "has conflicting property types declared: @${PropertyType1.simpleName}, @${PropertyType2.simpleName}",
            "has conflicting property types declared: @${PropertyType2.simpleName}, @${PropertyType1.simpleName}"
        ] as Set
    }

    class WithNonConflictingPropertyTypes {
        @PropertyType1
        @PropertyType1Override
        FileCollection classpath
    }

    def "doesn't warn about non-conflicting property types being specified"() {
        when:
        def metadata = extractor.extractPropertyMetadata(WithNonConflictingPropertyTypes)

        then:
        assertPropertyTypes(metadata, classpath: PropertyType1Override)
        metadata*.validationMessages.flatten().empty
    }

    static class SimpleType {
        @PropertyType1 String inputString
        @PropertyType1Override File inputFile
        @SupportingAnnotation("inputDirectory")
        @PropertyType2 File inputDirectory
        @IrrelevantAnnotation Object injectedService
    }

    def "can get annotated properties of simple type"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(SimpleType)

        then:
        assertPropertyTypes(typeMetadata,
            inputString: PropertyType1,
            inputFile: PropertyType1Override,
            inputDirectory: PropertyType2,
            injectedService: null
        )
    }

    static abstract class BaseClassWithGetters {
        @PropertyType2
        abstract Iterable<String> getStrings()
    }

    static class WithGetters extends BaseClassWithGetters {
        @PropertyType1
        boolean isBoolean() {
            return true
        }

        @SupportingAnnotation("getBoolean")
        boolean getBoolean() {
            return isBoolean()
        }

        @SupportingAnnotation("getStrings")
        @Override
        List<String> getStrings() {
            return ["some", "strings"]
        }
    }

    def "annotations are gathered from different getters"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(WithGetters)
        then:
        assertPropertyTypes(typeMetadata, boolean: PropertyType1, strings: PropertyType2)
    }

    private static class BaseType {
        @PropertyType1 String baseValue
        @PropertyType1 String superclassValue
        @PropertyType1 String superclassValueWithDuplicateAnnotation
        String nonAnnotatedBaseValue
    }

    private static class OverridingType extends BaseType {
        @Override
        String getSuperclassValue() {
            return super.getSuperclassValue()
        }

        @PropertyType1 @Override
        String getSuperclassValueWithDuplicateAnnotation() {
            return super.getSuperclassValueWithDuplicateAnnotation()
        }

        @PropertyType1 @Override
        String getNonAnnotatedBaseValue() {
            return super.getNonAnnotatedBaseValue()
        }
    }

    def "overridden properties inherit super-class annotations"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(OverridingType)

        then:
        assertPropertyTypes(typeMetadata,
            baseValue: PropertyType1,
            nonAnnotatedBaseValue: PropertyType1,
            superclassValue: PropertyType1,
            superclassValueWithDuplicateAnnotation: PropertyType1,
        )
    }

    private interface TaskSpec {
        @PropertyType1
        String getInterfaceValue()
    }

    private static class InterfaceImplementingType implements TaskSpec {
        @Override
        String getInterfaceValue() {
            "value"
        }
    }

    def "implemented properties inherit interface annotations"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(InterfaceImplementingType)

        then:
        assertPropertyTypes(typeMetadata,
            interfaceValue: PropertyType1
        )
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class IsGetterType {
        @PropertyType1
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

    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private field is recognized for is-getter"() {
        when:
        def typeMetadata = extractor.extractPropertyMetadata(IsGetterType)

        then:
        assertPropertyTypes(typeMetadata,
            feature1: PropertyType1,
            feature2: null
        )
    }

    private static void assertPropertyTypes(Map<String, ?> expectedPropertyTypes, Set<PropertyMetadata> typeMetadata) {
        def propertyTypes = typeMetadata.collectEntries { propertyMetadata ->
            [(propertyMetadata.propertyName): propertyMetadata.propertyType]
        }
        assert propertyTypes == expectedPropertyTypes
    }
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface PropertyType1 {
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface PropertyType2 {
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface PropertyType1Override {
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface SupportingAnnotation {
    String value()
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface IrrelevantAnnotation {
}
