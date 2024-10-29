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

import groovy.transform.Generated
import groovy.transform.Memoized
import groovy.transform.PackageScope
import org.gradle.api.file.FileCollection
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.annotations.AnnotationCategory
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import spock.lang.Issue
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method

import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE
import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class DefaultTypeAnnotationMetadataStoreTest extends Specification implements ValidationMessageChecker {
    private static final COLOR = new AnnotationCategory() {
        @Override
        String getDisplayName() {
            return "color"
        }

        @Override
        String toString() {
            return displayName
        }
    }

    private static final BAZ = new AnnotationCategory() {
        @Override
        String getDisplayName() {
            return "baz"
        }

        @Override
        String toString() {
            return displayName
        }
    }

    def store = new DefaultTypeAnnotationMetadataStore(
        [TestType],
        [(Large): TYPE, (Small): TYPE, (Color): COLOR],
        [(Foo): TYPE, (Bar): TYPE, (Baz): BAZ],
        ["java", "groovy"],
        [Object],
        [Object, GroovyObject],
        [MutableType, MutableSubType],
        [Ignored, Ignored2],
        [],
        { Method method -> method.isAnnotationPresent(Generated) },
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
        protected boolean getProtectedProperty() { true }

        @PackageScope
        boolean isPackageProperty() { false }

        @Irrelevant
        private String getPrivateProperty() { "private" }
    }

    def "finds annotated properties and functions"() {
        expect:
        assertProperties TypeWithAnnotatedProperty, [
            publicProperty: [(TYPE): Large],
            protectedProperty: [(TYPE): Large],
            packageProperty: [(TYPE): Small],
            privateProperty: [(TYPE): Small],
            injectedProperty: [(TYPE): Inject],
        ], [
            strict(privateGetterAnnotatedMessage { property('privateProperty').annotation(Small.simpleName).includeLink() })
        ]
        and:
        assertFunctions TypeWithAnnotatedFunction, [
            publicMethod: [(TYPE): Foo],
            protectedMethod: [(TYPE): Foo],
            packageMethod: [(TYPE): Bar],
            privateMethod: [(TYPE): Bar],
        ], [
            strict(privateFunctionAnnotatedMessage { method('privateMethod').annotation(Bar.simpleName).includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    class TypeWithAnnotatedProperty {
        @Large
        String getPublicProperty() { "public" }

        @Large
        protected boolean getProtectedProperty() { true }

        @Small
        @PackageScope
        boolean isPackageProperty() { false }

        @Small
        private String getPrivateProperty() { "private" }

        @Inject
        String getInjectedProperty() { "injected" }
    }

    @SuppressWarnings("unused")
    class TypeWithAnnotatedFunction {
        @Foo
        String publicMethod() { "public" }

        @Foo
        protected boolean protectedMethod() { true }

        @Bar
        @PackageScope
        boolean packageMethod() { false }

        @Bar
        private String privateMethod() { "private" }
    }

    def "ignores all functions and properties on type #type.simpleName"() {
        expect:
        assertProperties type, [:]
        assertFunctions type, [:]
        where:
        type << [int, EmptyGroovyObject, int[], Object[], Nullable]
    }

    class EmptyGroovyObject {}

    def "finds annotation on field"() {
        expect:
        assertProperties TypeWithFieldAnnotation, [
            largeProperty: [(TYPE): Large],
            injectedProperty: [(TYPE): Inject],
        ]
    }

    @SuppressWarnings("unused")
    class TypeWithFieldAnnotation {
        @Large
        private final String largeProperty = "test"

        String getLargeProperty() { largeProperty }

        @Inject
        private String injectedProperty

        String getInjectedProperty() { injectedProperty }
    }

    def "warns about annotation on field conflicting with annotation on getter and prefers getter annotation"() {
        expect:
        assertProperties TypeWithConflictingFieldAndMethodAnnotation, [
            property: [(TYPE): Small],
        ], [
            strict(conflictingAnnotationsMessage { property('property').inConflict('Small', 'Large').includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    class TypeWithConflictingFieldAndMethodAnnotation {
        @Large
        private final String property = "test"

        @Small
        String getProperty() { property }
    }

    def "warns about annotation on field without getter"() {
        expect:
        assertProperties TypeWithFieldOnlyAnnotation, [:], [
            strict(ignoredAnnotationOnField {
                type(TypeWithFieldOnlyAnnotation.canonicalName).property('property')
                    .annotatedWith('Large')
                    .includeLink()
            })
        ]
    }

    @SuppressWarnings("unused")
    class TypeWithFieldOnlyAnnotation {
        @Large
        private final String property = "test"

        // @Inject is allowed on a field only
        @Inject
        private String injectedProperty
    }

    def "detects ignored properties"() {
        expect:
        assertProperties TypeWithIgnoredProperty, [
            ignoredProperty: [(TYPE): Ignored]
        ]
    }

    @SuppressWarnings("unused")
    interface TypeWithIgnoredProperty {
        @Ignored
        String getIgnoredProperty()
    }

    def "ignores 'is' getter when 'get' getter is also defined"() {
        expect:
        assertProperties TypeWithIsAndGetProperty, [
            bool: [(TYPE): Small],
        ], [
            strict(redundantGetters {
                property('bool')
                    .includeLink()
            })
        ]
        store.getTypeAnnotationMetadata(TypeWithIsAndGetProperty).propertiesAnnotationMetadata[0].method.name == "getBool"
    }

    @SuppressWarnings("unused")
    class TypeWithIsAndGetProperty {

        @Small
        boolean getBool() { true }

        @Color
        boolean isBool() { true }
    }

    def "can have annotated Groovy boolean field without warnings"() {
        expect:
        assertProperties TypeWithGroovyBooleanProperty, [
            bool: [(TYPE): Small],
        ]
    }

    @SuppressWarnings("unused")
    class TypeWithGroovyBooleanProperty {
        @Small
        boolean bool
    }

    def "can ignore 'is' getter in favor of 'get' getter"() {
        expect:
        assertProperties TypeWithIgnoredIsGetterBooleanProperty, [
            bool: [(TYPE): Small],
        ]
        store.getTypeAnnotationMetadata(TypeWithIgnoredIsGetterBooleanProperty).propertiesAnnotationMetadata[0].method.name == "getBool"
    }

    @SuppressWarnings("unused")
    class TypeWithIgnoredIsGetterBooleanProperty {

        @Small
        boolean getBool() { true }

        @Ignored
        boolean isBool() { true }
    }

    def "can ignore 'get' getter in favor of 'is' getter"() {
        expect:
        assertProperties TypeWithIgnoredGetGetterBooleanProperty, [
            bool: [(TYPE): Small],
        ]
        store.getTypeAnnotationMetadata(TypeWithIgnoredGetGetterBooleanProperty).propertiesAnnotationMetadata[0].method.name == "isBool"
    }

    @SuppressWarnings("unused")
    class TypeWithIgnoredGetGetterBooleanProperty {

        @Ignored
        boolean getBool() { true }

        @Small
        boolean isBool() { true }
    }

    def "fails when ignored property has other annotations"() {
        expect:
        assertProperties TypeWithIgnoredPropertyWithOtherAnnotations, [
            ignoredProperty: [(TYPE): Ignored]
        ], [
            strict(ignoredAnnotatedPropertyMessage { property('ignoredProperty').ignoring('Ignored').alsoAnnotatedWith('Color').includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    interface TypeWithIgnoredPropertyWithOtherAnnotations {
        @Ignored
        @Color
        String getIgnoredProperty()
    }

    def "fails when ignored property has other ignore annotations"() {
        expect:
        assertProperties TypeWithIgnoredPropertyWithMultipleIgnoreAnnotations, [
            twiceIgnoredProperty: [(TYPE): Ignored]
        ], [
            strict(ignoredAnnotatedPropertyMessage { property('twiceIgnoredProperty').ignoring('Ignored').alsoAnnotatedWith('Ignored2').includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    interface TypeWithIgnoredPropertyWithMultipleIgnoreAnnotations {
        @Ignored
        @Ignored2
        String getTwiceIgnoredProperty()
    }

    def "fails when field is ignored but there is another annotation on the getter"() {
        expect:
        assertProperties TypeWithIgnoredFieldAndGetterInput, [
            ignoredByField: [(TYPE): Ignored]
        ], [
            strict(ignoredAnnotatedPropertyMessage { property('ignoredByField').ignoring('Ignored').alsoAnnotatedWith('Small').includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    static class TypeWithIgnoredFieldAndGetterInput {
        @Ignored
        private String ignoredByField

        @Small
        String getIgnoredByField() {
            return ignoredByField
        }
    }

    def "superclass properties and functions are present in subclass"() {
        expect:
        assertProperties TypeWithSuperclassProperties, [
            baseProperty: [(TYPE): Small],
            subclassProperty: [(TYPE): Large]
        ]
        and:
        assertFunctions TypeWithSuperclassFunctions, [
            baseMethod: [(TYPE): Foo],
            subclassMethod: [(TYPE): Bar]
        ]
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithSuperClassProperties {
        @Small
        String getBaseProperty()
    }

    @SuppressWarnings("unused")
    interface TypeWithSuperclassProperties extends BaseTypeWithSuperClassProperties {
        @Large
        String getSubclassProperty()
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithSuperClassFunctions {
        @Foo
        String baseMethod()
    }

    @SuppressWarnings("unused")
    interface TypeWithSuperclassFunctions extends BaseTypeWithSuperClassFunctions {
        @Bar
        String subclassMethod()
    }

    def "properties and functions are inherited from implemented interface"() {
        expect:
        assertProperties TypeWithInterfaceProperties, [
            interfaceProperty: [(TYPE): Small],
            subclassProperty: [(TYPE): Large]
        ]
        and:
        assertFunctions TypeWithInterfaceFunctions, [
            interfaceMethod: [(TYPE): Foo],
            subclassMethod: [(TYPE): Bar]
        ]
    }

    @SuppressWarnings("unused")
    interface InterfaceWithProperties {
        @Small
        String getInterfaceProperty()
    }

    @SuppressWarnings("unused")
    abstract class TypeWithInterfaceProperties implements InterfaceWithProperties {
        @Large
        abstract String getSubclassProperty()
    }

    @SuppressWarnings("unused")
    interface InterfaceWithFunctions {
        @Foo
        String interfaceMethod()
    }

    @SuppressWarnings("unused")
    abstract class TypeWithInterfaceFunctions implements InterfaceWithFunctions {
        @Bar
        abstract String subclassMethod()
    }

    def "overridden properties and functions inherit super-class annotations"() {
        expect:
        assertProperties TypeWithInheritedProperty, [
            overriddenProperty: [(COLOR): Color]
        ]
        and:
        assertFunctions TypeWithInheritedFunctions, [
            overriddenMethod: [(TYPE): Foo]
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

    @SuppressWarnings("unused")
    class BaseTypeWithInheritedFunctions {
        @Foo
        String overriddenMethod() { "test" }
    }

    @SuppressWarnings("unused")
    class TypeWithInheritedFunctions extends BaseTypeWithInheritedFunctions {
        @Override
        String overriddenMethod() { "test" }
    }

    def "annotation defined on implemented interface takes precedence over superclass annotation"() {
        expect:
        assertProperties TypeWithInheritedPropertyFromSuperClassAndInterface, [
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "interface" }]
        ]

        and:
        assertFunctions TypeWithInheritedFunctionFromSuperClassAndInterface, [
            overriddenMethod: [(TYPE): Foo]
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
        implements InterfaceWithInheritedProperty {
        @Override
        String getOverriddenProperty() { "test" }
    }

    @SuppressWarnings("unused")
    interface InterfaceWithInheritedFunction {
        @Foo
        String getOverriddenProperty()
    }

    @SuppressWarnings("unused")
    class TypeWithInheritedFunctionFromSuperClassAndInterface
        extends BaseTypeWithInheritedFunctions
        implements InterfaceWithInheritedFunction {
        @Override
        String getOverriddenProperty() { "test" }
    }

    def "implemented properties and functions inherit annotation from first conflicting interface"() {
        expect:
        assertProperties TypeWithImplementedPropertyFromInterfaces, [
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "first-interface" }]
        ], [
            strict(conflictingAnnotationsMessage { property('overriddenProperty').inConflict('Color', 'Color').includeLink().kind('color annotations inherited (from interface)') })
        ]
        and:
        assertFunctions TypeWithImplementedFunctionFromInterfaces, [
            overriddenMethod: [(BAZ): { it instanceof Baz && it.declaredBy() == "first-interface" }]
        ], [
            strict(conflictingAnnotationsMessage { method('overriddenMethod').inConflict('Baz', 'Baz').includeLink().kind('baz annotations inherited (from interface)') })
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
        implements FirstInterfaceWithInheritedProperty, SecondInterfaceWithInheritedProperty {
        @Override
        String getOverriddenProperty() { "test" }
    }

    @SuppressWarnings("unused")
    interface FirstInterfaceWithInheritedFunction {
        @Baz(declaredBy = "first-interface")
        String overriddenMethod()
    }

    @SuppressWarnings("unused")
    interface SecondInterfaceWithInheritedFunction {
        @Baz(declaredBy = "second-interface")
        String overriddenMethod()
    }

    @SuppressWarnings("unused")
    class TypeWithImplementedFunctionFromInterfaces
        implements FirstInterfaceWithInheritedFunction, SecondInterfaceWithInheritedFunction {
        @Override
        String overriddenMethod() { "test" }
    }

    def "subtype can resolve conflicting annotations from implemented interfaces"() {
        expect:
        assertProperties TypeOverridingPropertyFromConflictingInterfaces, [
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "subtype" }]
        ]
        and:
        assertFunctions TypeOverridingFunctionFromConflictingInterfaces, [
            overriddenMethod: [(BAZ): { it instanceof Baz && it.declaredBy() == "subtype" }]
        ]
    }

    @SuppressWarnings("unused")
    class TypeOverridingPropertyFromConflictingInterfaces
        implements FirstInterfaceWithInheritedProperty, SecondInterfaceWithInheritedProperty {
        @Override
        @Color(declaredBy = "subtype")
        String getOverriddenProperty() { "test" }
    }

    @SuppressWarnings("unused")
    class TypeOverridingFunctionFromConflictingInterfaces
        implements FirstInterfaceWithInheritedFunction, SecondInterfaceWithInheritedFunction {
        @Override
        @Baz(declaredBy = "subtype")
        String overriddenMethod() { "test" }
    }

    def "can override same annotation in subclass"() {
        expect:
        assertProperties TypeWithPropertyOverride, [
            overriddenProperty: [(COLOR): { it instanceof Color && it.declaredBy() == "override" }]
        ]
        and:
        assertFunctions TypeWithFunctionOverride, [
            overriddenMethod: [(BAZ): { it instanceof Baz && it.declaredBy() == "override" }]
        ]
    }

    def "property getter or function is from overriding class"() {
        when:
        def propertyMetadata = store.getTypeAnnotationMetadata(TypeWithPropertyOverride)
        then:
        propertyMetadata.propertiesAnnotationMetadata[0].method.declaringClass == TypeWithPropertyOverride
        when:
        def functionMetadata = store.getTypeAnnotationMetadata(TypeWithFunctionOverride)
        then:
        functionMetadata.functionAnnotationMetadata[0].method.declaringClass == TypeWithFunctionOverride
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithPropertyOverride {
        @Color(declaredBy = "base")
        String getOverriddenProperty()
    }

    @SuppressWarnings("unused")
    interface TypeWithPropertyOverride extends BaseTypeWithPropertyOverride {
        @Override
        @Color(declaredBy = "override")
        String getOverriddenProperty()
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithFunctionOverride {
        @Baz(declaredBy = "base")
        String overriddenMethod()
    }

    @SuppressWarnings("unused")
    interface TypeWithFunctionOverride extends BaseTypeWithFunctionOverride {
        @Override
        @Baz(declaredBy = "override")
        String overriddenMethod()
    }

    def "can override annotation with different annotation in same category in subclass"() {
        expect:
        assertProperties CanOverrideOverridePropertyCategoryClass, [
            overriddenProperty: [(TYPE): Small, (COLOR): Color]
        ]
        and:
        assertFunctions CanOverrideOverrideFunctionCategoryClass, [
            overriddenMethod: [(TYPE): Bar, (BAZ): Baz]
        ]
    }

    @SuppressWarnings("unused")
    interface CanOverridePropertyCategoryBaseClass {
        @Large
        String getOverriddenProperty()
    }

    interface CanOverridePropertyCategoryIntermediateClass extends CanOverridePropertyCategoryBaseClass {
        @Override
        @Color
        String getOverriddenProperty()
    }

    interface CanOverrideOverridePropertyCategoryClass extends CanOverridePropertyCategoryIntermediateClass {
        @Override
        @Small
        String getOverriddenProperty()
    }

    @SuppressWarnings("unused")
    interface CanOverrideFunctionCategoryBaseClass {
        @Foo
        String overriddenMethod()
    }

    interface CanOverrideFunctionCategoryIntermediateClass extends CanOverrideFunctionCategoryBaseClass {
        @Override
        @Baz
        String overriddenMethod()
    }

    interface CanOverrideOverrideFunctionCategoryClass extends CanOverrideFunctionCategoryIntermediateClass {
        @Override
        @Bar
        String overriddenMethod()
    }

    def "can ignore supertype property"() {
        expect:
        assertProperties TypeHidingPropertyFromSuperType, [
            baseProperty: [(TYPE): Ignored],
        ]
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithPropertyToHide {
        @Color
        String getBaseProperty()
    }

    @SuppressWarnings("unused")
    interface TypeHidingPropertyFromSuperType extends BaseTypeWithPropertyToHide {
        @Ignored
        @Override
        String getBaseProperty()
    }

    def "can redefine ignored supertype property"() {
        expect:
        assertProperties TypeRedefiningIgnoredPropertyFromSuperType, [
            propertyIgnoredInBase: [(TYPE): Small]
        ]
    }

    @SuppressWarnings("unused")
    interface BaseTypeWithIgnoredProperty {
        @Ignored
        String getPropertyIgnoredInBase()
    }

    @SuppressWarnings("unused")
    interface TypeRedefiningIgnoredPropertyFromSuperType extends BaseTypeWithIgnoredProperty {
        @Override
        @Small
        String getPropertyIgnoredInBase()
    }

    def "warns about conflicting property or function types being specified, chooses first declaration"() {
        expect:
        assertProperties TypeWithPropertiesWithMultipleAnnotationsOfSameCategory, [
            largeThenSmall: [(TYPE): Large],
            smallThenLarge: [(TYPE): Small]
        ], [
            strict(conflictingAnnotationsMessage { property('largeThenSmall').inConflict('Large', 'Small').includeLink() }),
            strict(conflictingAnnotationsMessage { property('smallThenLarge').inConflict('Small', 'Large').includeLink() })
        ]
        and:
        assertFunctions TypeWithFunctionsWithMultipleAnnotationsOfSameCategory, [
            largeThenSmall: [(TYPE): Foo],
            smallThenLarge: [(TYPE): Bar]
        ], [
            strict(conflictingAnnotationsMessage { method('largeThenSmall').inConflict('Foo', 'Bar').includeLink() }),
            strict(conflictingAnnotationsMessage { method('smallThenLarge').inConflict('Bar', 'Foo').includeLink() })
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

    @SuppressWarnings("unused")
    interface TypeWithFunctionsWithMultipleAnnotationsOfSameCategory {
        @Foo
        @Bar
        String largeThenSmall()

        @Bar
        @Foo
        String smallThenLarge()
    }

    def "warns about both method and field having the same annotation, prefers method annotation"() {
        expect:
        assertProperties WithBothFieldAndGetterAnnotation, [
            inputFiles: [(COLOR): { it instanceof Color && it.declaredBy() == "method" }]
        ], [
            strict(conflictingAnnotationsMessage { property('inputFiles').inConflict('Color', 'Color').includeLink().kind('color annotations declared') })
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

    def "report setters for property of mutable type"() {
        expect:
        assertProperties TypeWithPropertiesWithMutableProperties, [
            mutableSubType: [(TYPE): Small],
            mutableType: [(TYPE): Small],
            mutableSubTypeWithSetter: [(TYPE): Small],
            mutableTypeWithSetter: [(TYPE): Small]
        ], [
            strict(mutableSetter { property('mutableSubTypeWithSetter').propertyType(MutableSubType.name).includeLink() }),
            strict(mutableSetter { property('mutableTypeWithSetter').propertyType(MutableType.name).includeLink() })
        ]
    }

    @SuppressWarnings("unused")
    interface TypeWithPropertiesWithMutableProperties {
        @Small
        MutableType getMutableType()

        @Small
        MutableSubType getMutableSubType()

        @Small
        MutableType getMutableTypeWithSetter()

        void setMutableTypeWithSetter(MutableType value)

        @Small
        MutableSubType getMutableSubTypeWithSetter()

        void setMutableSubTypeWithSetter(MutableSubType value)
    }

    class MutableType {}

    class MutableSubType extends MutableType {}

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

    def "warns about annotations on private properties or functions"() {
        expect:
        assertProperties WithAnnotationsOnPrivateProperty, [
            privateInput: [(TYPE): Large]
        ], [
            strict(privateGetterAnnotatedMessage { property('privateInput').annotation(Large.simpleName).includeLink() })
        ]
        and:
        assertFunctions WithAnnotationsOnPrivateFunction, [
            privateMethod: [(TYPE): Foo]
        ], [
            strict(privateFunctionAnnotatedMessage { method('privateMethod').annotation(Foo.simpleName).includeLink() })
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

    @SuppressWarnings("unused")
    class WithAnnotationsOnPrivateFunction {
        @Foo
        private String privateMethod() {
            'Input'
        }

        private String anotherMethod() {
            'Not an input'
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private field is recognized for is-getter"() {
        expect:
        assertProperties IsGetterType, [
            feature1: [(TYPE): Large],
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
        def metadata = store.getTypeAnnotationMetadata(TypeWithAnnotations)

        then:
        metadata.annotations*.annotationType() == [TestType]
    }

    @TestType
    @Irrelevant
    interface TypeWithAnnotations {}

    def "warns about property annotations on function methods"() {
        expect:
        assertProperties TypeWithAnnotatedNonGetterMethods, [:], [
            strict(methodShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedNonGetterMethods.canonicalName).kind('function').method('doSomething').annotation('Large').includeLink() }),
            strict(methodShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedNonGetterMethods.canonicalName).kind('setter').method('setSomething').annotation('Large').includeLink() }),
            strict(methodShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedNonGetterMethods.canonicalName).kind('static method').method('doStatic').annotation('Large').includeLink() }),
            strict(methodShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedNonGetterMethods.canonicalName).kind('static method').method('getStatic').annotation('Small').includeLink() }),
        ]
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class TypeWithAnnotatedNonGetterMethods {
        @Large
        static void doStatic() {}

        @Small
        static String getStatic() { "static" }

        @Large
        void doSomething() {}

        @Large
        void setSomething(String something) {}
    }

    def "warns about function annotations on fields or property methods"() {
        expect:
        assertFunctions TypeWithAnnotatedGetterMethods, [:], [
            strict(fieldShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedGetterMethods.canonicalName).kind('field').method('anything').annotation('Foo').includeLink() }),
            strict(propertyShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedGetterMethods.canonicalName).kind('property').method('getSomething').annotation('Foo').includeLink() }),
            strict(staticPropertyMethodShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedGetterMethods.canonicalName).kind('static method').method('getStatic').annotation('Bar').includeLink() }),
            strict(propertyShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedGetterMethods.canonicalName).kind('property').method('isSomethingElse').annotation('Bar').includeLink() }),
            strict(propertyShouldNotBeAnnotatedMessage { type(TypeWithAnnotatedGetterMethods.canonicalName).kind('property').method('setSomething').annotation('Foo').includeLink() }),
        ]
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class TypeWithAnnotatedGetterMethods {
        @Foo
        String anything

        @Foo
        String getSomething() {}

        @Bar
        static String getStatic() { "static" }

        @Bar
        boolean isSomethingElse() {}

        @Foo
        void setSomething(String something) {}
    }

    def "ignores validation of generated Groovy methods"() {
        expect:
        assertProperties TypeWithGeneratedGroovyMethods, [someValue: [(TYPE): Large]]
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class TypeWithGeneratedGroovyMethods {
        @Memoized
        @Large
        String getSomeValue() { "foo" }
    }

    def "does not detect methods on type from ignored package"() {
        expect:
        assertProperties ArrayList, [:]
        and:
        assertFunctions ArrayList, [:]
    }

    void assertProperties(Class<?> type, Map<String, Map<AnnotationCategory, ?>> expectedProperties, List<String> expectedErrors = []) {
        def metadata = store.getTypeAnnotationMetadata(type)
        def actualPropertyNames = metadata.propertiesAnnotationMetadata*.propertyName.sort()
        def expectedPropertyNames = expectedProperties.keySet().sort()
        assert actualPropertyNames == expectedPropertyNames

        metadata.propertiesAnnotationMetadata.forEach { actualProperty ->
            def expectedAnnotations = expectedProperties[actualProperty.propertyName]

            def actualCategories = actualProperty.annotationsByCategory.keySet().sort()
            def expectedCategories = expectedAnnotations.keySet().sort()
            assert actualCategories == expectedCategories

            actualCategories.forEach { category ->
                def actualAnnotation = actualProperty.annotationsByCategory[category]
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

        def validationContext = DefaultTypeValidationContext.withoutRootType(false, Stub(InternalProblems.class))
        metadata.visitValidationFailures(validationContext)
        List<String> actualErrors = validationContext.problems
            .collect({ (normaliseLineSeparators(TypeValidationProblemRenderer.renderMinimalInformationAbout(it)) + (it.definition.severity == Severity.ERROR ? " [STRICT]" : "") as String) })
        actualErrors.sort()
        expectedErrors.sort()
        assert actualErrors == expectedErrors
    }

    void assertFunctions(Class<?> type, Map<String, Map<AnnotationCategory, ?>> expectedFunctions, List<String> expectedErrors = []) {
        def metadata = store.getTypeAnnotationMetadata(type)
        def actualMethodNames = metadata.functionAnnotationMetadata*.method.name.sort()
        def expectedMethodNames = expectedFunctions.keySet().sort()
        assert actualMethodNames == expectedMethodNames

        metadata.functionAnnotationMetadata.forEach { actualMethod ->
            def expectedAnnotations = expectedFunctions[actualMethod.method.name]

            def actualCategories = actualMethod.annotationsByCategory.keySet().sort()
            def expectedCategories = expectedAnnotations.keySet().sort()
            assert actualCategories == expectedCategories

            actualCategories.forEach { category ->
                def actualAnnotation = actualMethod.annotationsByCategory[category]
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

        def validationContext = DefaultTypeValidationContext.withoutRootType(false, Stub(InternalProblems.class))
        metadata.visitValidationFailures(validationContext)
        List<String> actualErrors = validationContext.problems
            .collect({ (normaliseLineSeparators(TypeValidationProblemRenderer.renderMinimalInformationAbout(it)) + (it.definition.severity == Severity.ERROR ? " [STRICT]" : "") as String) })
        actualErrors.sort()
        expectedErrors.sort()
        assert actualErrors == expectedErrors
    }

    private static String strict(String message) {
        "$message [STRICT]"
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

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Ignored {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Ignored2 {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.FIELD])
@interface Foo {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD])
@interface Bar {
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD])
@interface Baz {
    String declaredBy() default ""
}
