/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect
import org.gradle.model.*
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.manage.schema.extract.ModelStoreTestUtils
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

import java.beans.Introspector

class ModelRuleExtractorTest extends Specification {
    ModelRegistry registry = new DefaultModelRegistry(null)
    def extractor = new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.instance, new DefaultNodeInitializerRegistry(DefaultModelSchemaStore.instance, new DefaultConstructableTypesRegistry())))

    static class ModelThing {
        final String name

        ModelThing(String name) {
            this.name = name
        }
    }

    static class EmptyClass extends RuleSource {}

    def "can inspect class with no rules"() {
        expect:
        extractor.extract(EmptyClass).empty
    }

    static class SimpleModelCreationRuleInferredName extends RuleSource {
        @Model
        static ModelThing modelPath() {
            new ModelThing("foo")
        }
    }

    List<ExtractedModelRule> extract(Class<?> source) {
        extractor.extract(source)
    }

    void registerRules(Class<?> clazz) {
        def rules = extract(clazz)
        rules.each {
            it.apply(registry, ModelPath.ROOT)
        }
    }

    def "can inspect class with simple model creation rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def rule = extract(SimpleModelCreationRuleInferredName).first()

        then:
        rule instanceof ExtractedModelCreator

        when:
        rule.apply(mockRegistry, ModelPath.ROOT)

        then:
        1 * mockRegistry.create(_) >> { ModelCreator creator ->
            assert creator.path.toString() == "modelPath"
        }
        0 * _
    }

    static class ParameterizedModel extends RuleSource {
        @Model
        List<String> strings() {
            Arrays.asList("foo")
        }

        @Model
        List<? super String> superStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<? extends String> extendsStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<?> wildcard() {
            Arrays.asList("foo")
        }
    }

    def "can inspect class with model creation rule for paramaterized type"() {
        when:
        registerRules(ParameterizedModel)

        then:
        registry.realizeNode(ModelPath.path("strings")).promise.canBeViewedAsReadOnly(new ModelType<List<String>>() {})
        registry.realizeNode(ModelPath.path("superStrings")).promise.canBeViewedAsReadOnly(new ModelType<List<? super String>>() {})
        registry.realizeNode(ModelPath.path("extendsStrings")).promise.canBeViewedAsReadOnly(new ModelType<List<? extends String>>() {})
        registry.realizeNode(ModelPath.path("wildcard")).promise.canBeViewedAsReadOnly(new ModelType<List<?>>() {})
    }

    static class HasGenericModelRule extends RuleSource {
        @Model
        static <T> List<T> thing() {
            []
        }
    }

    def "model creation rule cannot be generic"() {
        when:
        registerRules(HasGenericModelRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == "$HasGenericModelRule.name#thing() is not a valid model rule method: cannot have type variables (i.e. cannot be a generic method)"
    }

    static class HasMultipleRuleAnnotations extends RuleSource {
        @Model
        @Mutate
        static String thing() {
            ""
        }
    }

    def "model rule method cannot be annotated with multiple rule annotations"() {
        when:
        registerRules(HasMultipleRuleAnnotations)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == "${ModelType.of(HasMultipleRuleAnnotations).simpleName}#thing is not a valid model rule method: can only be one of [annotated with @Model and returning a model element, @annotated with @Model and taking a managed model element, annotated with @Defaults, annotated with @Mutate, annotated with @Finalize, annotated with @Validate]"
    }

    static class ConcreteGenericModelType extends RuleSource {
        @Model
        static List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured"() {
        when:
        registerRules(ConcreteGenericModelType)
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asReadOnly(new ModelType<List<String>>() {}, node, null).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class ConcreteGenericModelTypeImplementingGenericInterface extends RuleSource implements HasStrings<String> {
        @Model
        List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured when method is generic in interface"() {
        when:
        registerRules(ConcreteGenericModelTypeImplementingGenericInterface)
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asReadOnly(new ModelType<List<String>>() {}, node, null).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class HasRuleWithIdentityCrisis extends RuleSource {
        @Mutate
        @Model
        void foo() {}
    }

    def "rule cannot be of more than one type"() {
        when:
        registerRules(HasRuleWithIdentityCrisis)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class GenericMutationRule extends RuleSource {
        @Mutate
        <T> void mutate(T thing) {}
    }

    def "mutation rule cannot be generic"() {
        when:
        registerRules(GenericMutationRule)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class NonVoidMutationRule extends RuleSource {
        @Mutate
        String mutate(String thing) {}
    }

    def "only void is allowed as return type of a mutation rule"() {
        when:
        registerRules(NonVoidMutationRule)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class RuleWithEmptyInputPath extends RuleSource {
        @Model
        String create(@Path("") String thing) {}
    }

    def "path of rule input cannot be empty"() {
        when:
        registerRules(RuleWithEmptyInputPath)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class RuleWithInvalidInputPath extends RuleSource {
        @Model
        String create(@Path("!!!!") String thing) {}
    }

    def "path of rule input has to be valid"() {
        when:
        registerRules(RuleWithInvalidInputPath)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class MutationRules extends RuleSource {
        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }

        @Mutate
        static void mutate2(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }
    }

    // Not an exhaustive test of the mechanics of mutation rules, just testing the extraction and registration
    def "mutation rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.create(ModelCreators.bridgedInstance(ModelReference.of(path, type), []).descriptor("strings").build())

        when:
        registerRules(MutationRules)


        then:
        def node = registry.realizeNode(path)
        node.adapter.asReadOnly(type, node, null).instance.sort() == ["1", "2"]
    }

    static class MutationAndFinalizeRules extends RuleSource {
        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }

        @Finalize
        static void finalize1(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }
    }

    // Not an exhaustive test of the mechanics of finalize rules, just testing the extraction and registration
    def "finalize rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.create(ModelCreators.bridgedInstance(ModelReference.of(path, type), []).descriptor("strings").build())

        when:
        registerRules(MutationAndFinalizeRules)

        then:
        def node = registry.realizeNode(path)
        node.adapter.asReadOnly(type, node, null).instance == ["1", "2"]
    }

    def "methods are processed ordered by their to string representation"() {
        when:
        def stringListType = new ModelType<List<String>>() {}
        def integerListType = new ModelType<List<Integer>>() {}

        registry.create(ModelCreators.bridgedInstance(ModelReference.of(ModelPath.path("strings"), stringListType), []).descriptor("strings").build())
        registry.create(ModelCreators.bridgedInstance(ModelReference.of(ModelPath.path("integers"), integerListType), []).descriptor("integers").build())

        then:
        extractor.extract(MutationAndFinalizeRules)*.action*.descriptor == [
            MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "finalize1"),
            MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "mutate1"),
            MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "mutate3")
        ]

    }

    static class InvalidModelNameViaAnnotation extends RuleSource {
        @Model(" ")
        String foo() {
            "foo"
        }
    }

    def "invalid model name is not allowed"() {
        when:
        registerRules(InvalidModelNameViaAnnotation)

        then:
        thrown InvalidModelRuleDeclarationException
    }

    static class RuleSetCreatingAnInterfaceThatIsNotAnnotatedWithManaged extends RuleSource {
        @Model
        void bar(NonManaged foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be @Managed annotated"() {
        when:
        registerRules(RuleSetCreatingAnInterfaceThatIsNotAnnotatedWithManaged)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ModelRuleExtractorTest.RuleSetCreatingAnInterfaceThatIsNotAnnotatedWithManaged#bar is invalid."
        e.cause instanceof ModelTypeInitializationException
        e.cause.message == "The model node of type: '$NonManaged.name' can not be constructed. The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]"
    }

    static class RuleSourceCreatingAClassAnnotatedWithManaged extends RuleSource {
        @Model
        void bar(ManagedAnnotatedClass foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be a valid managed type"() {
        when:
        registerRules(RuleSourceCreatingAClassAnnotatedWithManaged)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == 'Declaration of model rule ModelRuleExtractorTest.RuleSourceCreatingAClassAnnotatedWithManaged#bar is invalid.'
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == "Invalid managed model type $ManagedAnnotatedClass.name: must be defined as an interface or an abstract class."
    }

    static class RuleSourceWithAVoidReturningNoArgumentMethod extends RuleSource {
        @Model
        void bar() {
        }
    }

    def "void returning model definition has to take at least one argument"() {
        when:
        registerRules(RuleSourceWithAVoidReturningNoArgumentMethod)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == 'ModelRuleExtractorTest.RuleSourceWithAVoidReturningNoArgumentMethod#bar is not a valid model rule method: a void returning model element creation rule has to take a managed model element instance as the first argument'
    }

    static class RuleSourceCreatingManagedWithNestedPropertyOfInvalidManagedType extends RuleSource {
        @Model
        void bar(ManagedWithNestedPropertyOfInvalidManagedType foo) {
        }
    }

    static class RuleSourceCreatingManagedWithNestedReferenceOfInvalidManagedType extends RuleSource {
        @Model
        void bar(ManagedWithNestedReferenceOfInvalidManagedType foo) {
        }
    }

    @Unroll
    def "void returning model definition with for a type with a nested property of invalid managed type - #inspected.simpleName"() {
        when:
        registerRules(inspected)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ${getClass().simpleName}.$inspected.simpleName#bar is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $invalidTypeName: cannot be a parameterized type.
The type was analyzed due to the following dependencies:
${managedType.name}
  \\--- property 'managedWithNestedInvalidManagedType' (${nestedManagedType.name})
    \\--- property 'invalidManaged' ($invalidTypeName)""")

        where:
        inspected                                                        | managedType                                    | nestedManagedType
        RuleSourceCreatingManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithPropertyOfInvalidManagedType
        RuleSourceCreatingManagedWithNestedReferenceOfInvalidManagedType | ManagedWithNestedReferenceOfInvalidManagedType | ManagedWithReferenceOfInvalidManagedType

        invalidTypeName = "$ParametrizedManaged.name<$String.name>"
    }

    static class RuleSourceCreatingManagedWithNonManageableParent extends RuleSource {
        @Model
        void bar(ManagedWithNonManageableParents foo) {
        }
    }

    def "error message produced when super type is not a manageable type indicates the original (sub) type"() {
        when:
        registerRules(RuleSourceCreatingManagedWithNonManageableParent)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ${ModelType.of(RuleSourceCreatingManagedWithNonManageableParent).simpleName}#bar is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $invalidTypeName: cannot be a parameterized type.
The type was analyzed due to the following dependencies:
${ManagedWithNonManageableParents.name}
  \\--- property 'invalidManaged' declared by ${AnotherManagedWithPropertyOfInvalidManagedType.name}, ${ManagedWithPropertyOfInvalidManagedType.name} ($invalidTypeName)""")

        where:
        invalidTypeName = "$ParametrizedManaged.name<$String.name>"
    }

    static class HasRuleWithUncheckedModelMap extends RuleSource {
        @Model
        static ModelThing modelPath(ModelMap foo) {
            new ModelThing("foo")
        }
    }

    def "error when trying to use model map without specifying type param"() {
        when:
        registerRules(HasRuleWithUncheckedModelMap)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "$HasRuleWithUncheckedModelMap.name#modelPath(org.gradle.model.ModelMap) is not a valid model rule method: raw type org.gradle.model.ModelMap used for parameter 1 (all type parameters must be specified of parameterized type)"
    }

    def "extracted rules are cached"() {
        when:
        def fromFirstExtraction = extractor.extract(MutationRules)
        def fromSecondExtraction = extractor.extract(MutationRules)

        then:
        fromFirstExtraction.is(fromSecondExtraction)
    }

    def "cache does not hold strong references"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)
        def source = cl.parseClass('''
            import org.gradle.model.*

            class Rules extends RuleSource {
                @Mutate
                void mutate(String value) {
                }
            }
        ''')

        when:
        extractor.extract(source)

        then:
        extractor.cache.size() == 1

        when:
        cl.clearCache()
        forcefullyClearReferences(source)
        source = null

        then:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            extractor.cache.cleanUp()
            extractor.cache.size() == 0
        }
    }

    private void forcefullyClearReferences(Class<?> clazz) {
        ModelStoreTestUtils.removeClassFromGlobalClassSet(clazz)

        // Remove soft references
        Introspector.flushFromCaches(clazz)
    }
}
