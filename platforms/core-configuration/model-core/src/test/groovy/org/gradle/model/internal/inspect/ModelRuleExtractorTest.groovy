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
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.manage.schema.extract.ModelStoreTestUtils
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.test.fixtures.ConcurrentTestUtil

import java.beans.Introspector

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class ModelRuleExtractorTest extends ProjectRegistrySpec {
    def extractor = new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(SCHEMA_STORE), MANAGED_PROXY_FACTORY, SCHEMA_STORE, STRUCT_BINDINGS_STORE)
    ModelRegistry registry = new DefaultModelRegistry(extractor, null)

    static class ModelThing {
        final String name

        ModelThing(String name) {
            this.name = name
        }
    }

    static class EmptyClass extends RuleSource {}

    def "can inspect class with no rules"() {
        expect:
        extract(EmptyClass).empty
    }

    static class ClassWithNonRuleMethods extends RuleSource {
        static List thing() {
            []
        }

        static <T> List<T> genericThing() {
            []
        }

        private doStuff() {}

        private <T> T selectThing(List<T> list) { null }
    }

    def "can have non-rule methods that would be invalid rules"() {
        expect:
        extract(ClassWithNonRuleMethods).empty
    }

    static abstract class AbstractRules extends RuleSource {}

    def "rule class can be abstract"() {
        expect:
        extract(AbstractRules).empty
    }

    def "can create instance of abstract rule class"() {
        expect:
        def schema = extractor.extract(AbstractRules)
        schema.factory.create() instanceof AbstractRules
    }

    static abstract class AbstractPropertyRules extends RuleSource {
        @RuleInput
        abstract String getValue()
        abstract void setValue(String value)
        @RuleInput
        abstract int getNumber()
        abstract void setNumber(int value)
    }

    def "rule class can have abstract getter and setter"() {
        expect:
        extract(AbstractPropertyRules).empty
    }

    def "can create instance of rule class with abstract getter and setter"() {
        when:
        def schema = extractor.extract(AbstractPropertyRules)
        def instance = schema.factory.create()

        then:
        instance instanceof AbstractPropertyRules
        instance.value == null
        instance.number == 0

        when:
        instance.value = "12"
        instance.number = 12

        then:
        instance.value == "12"
        instance.number == 12
    }

    def "state is reused for all instances creates from a given extracted rule source"() {
        given:
        def schema = extractor.extract(AbstractPropertyRules)
        def instance = schema.factory.create()
        instance.value = "12"
        instance.number = 12

        expect:
        def sameSchema = schema.factory.create()
        sameSchema.value == "12"
        sameSchema.number == 12

        def schema2 = extractor.extract(AbstractPropertyRules)
        def differentSchema = schema2.factory.create()
        differentSchema.value == null
        differentSchema.number == 0
    }

    def "Java rule class can have non-public getters, setters and rule methods"() {
        expect:
        def schema = extractor.extract(AbstractJavaPropertyRules)
        schema.rules.size() == 2
        schema.factory.create() != null
    }

    static abstract class AbstractMethodsRules extends RuleSource {
        @Mutate
        abstract void thing(String s)
    }

    static class NotRuleSource {
    }

    @Managed
    static abstract class ManagedThing {
    }

    def "rule class must extend RuleSource"() {
        when:
        extract(type)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(type)} is not a valid rule source:
- Rule source classes must directly extend org.gradle.model.RuleSource"""

        where:
        type << [Long, RuleSource, NotRuleSource, ManagedThing]
    }

    def "rule class cannot have abstract rule methods"() {
        when:
        extract(AbstractMethodsRules)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(AbstractMethodsRules)} is not a valid rule source:
- Method thing(java.lang.String) is not a valid rule method: A rule method cannot be abstract"""
    }

    def "rule class cannot have Groovy meta methods"() {
        when:
        extract(WithGroovyMeta).empty

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(WithGroovyMeta)} is not a valid rule source:
- Method methodMissing(java.lang.String, java.lang.Object) is not a valid rule method: A method that is not annotated as a rule must be private
- Method propertyMissing(java.lang.String) is not a valid rule method: A method that is not annotated as a rule must be private
- Method propertyMissing(java.lang.String, java.lang.Object) is not a valid rule method: A method that is not annotated as a rule must be private"""
    }

    static class SimpleModelCreationRuleInferredName extends RuleSource {
        @Model
        static ModelThing modelPath() {
            new ModelThing("foo")
        }
    }

    <T> List<ExtractedModelRule> extract(Class<T> source) {
        (extractor.extract(source) as ModelRuleExtractor.DefaultExtractedRuleSource).rules
    }

    def "can inspect class with simple model creation rule"() {
        def registry = Mock(ModelRegistry)

        when:
        extractor.extract(SimpleModelCreationRuleInferredName).apply(registry, node())

        then:
        1 * registry.register(_) >> { ModelRegistration registration ->
            assert registration.path.toString() == "modelPath"
            assert registration.descriptor.toString() == "ModelRuleExtractorTest.SimpleModelCreationRuleInferredName#modelPath()"
        }
        0 * _
    }

    def "can create instance of rule class"() {
        expect:
        def schema = extractor.extract(SimpleModelCreationRuleInferredName)
        schema.factory.create() instanceof SimpleModelCreationRuleInferredName
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

    def "can inspect class with model creation rule for parameterized type"() {
        when:
        extractor.extract(ParameterizedModel).apply(registry, node())

        then:
        registry.realizeNode(ModelPath.path("strings")).promise.canBeViewedAs(new ModelType<List<String>>() {})
        registry.realizeNode(ModelPath.path("superStrings")).promise.canBeViewedAs(new ModelType<List<? super String>>() {})
        registry.realizeNode(ModelPath.path("extendsStrings")).promise.canBeViewedAs(new ModelType<List<? extends String>>() {})
        registry.realizeNode(ModelPath.path("wildcard")).promise.canBeViewedAs(new ModelType<List<?>>() {})
    }

    static class HasGenericModelRule extends RuleSource {
        @Model
        static <T> List<T> thing() {
            []
        }
    }

    def "model creation rule cannot be generic"() {
        when:
        extract(HasGenericModelRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(HasGenericModelRule)} is not a valid rule source:
- Method thing() is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)"""
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
        extract(HasMultipleRuleAnnotations)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(HasMultipleRuleAnnotations)} is not a valid rule source:
- Method thing() is not a valid rule method: Can only be one of [annotated with @Model and returning a model element, annotated with @Model and taking a managed model element, annotated with @Defaults, annotated with @Mutate, annotated with @Finalize, annotated with @Validate, annotated with @Rules]"""
    }

    static class ConcreteGenericModelType extends RuleSource {
        @Model
        static List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured"() {
        when:
        extractor.extract(ConcreteGenericModelType).apply(registry, node())
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asImmutable(new ModelType<List<String>>() {}, node, null).type

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
        extractor.extract(ConcreteGenericModelTypeImplementingGenericInterface).apply(registry, node())
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asImmutable(new ModelType<List<String>>() {}, node, null).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class GenericMutationRule extends RuleSource {
        @Mutate
        <T> void mutate(T thing) {}
    }

    def "mutation rule cannot be generic"() {
        when:
        extract(GenericMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(GenericMutationRule)} is not a valid rule source:
- Method mutate(T) is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)"""
    }

    static class NonVoidMutationRule extends RuleSource {
        @Mutate
        String mutate(String thing) {}
    }

    def "only void is allowed as return type of a mutation rule"() {
        when:
        extract(NonVoidMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(NonVoidMutationRule)} is not a valid rule source:
- Method mutate(java.lang.String) is not a valid rule method: A method annotated with @Mutate must have void return type."""
    }

    static class NoSubjectMutationRule extends RuleSource {
        @Mutate
        void mutate() {}
    }

    def "mutation rule must have a subject"() {
        when:
        extract(NoSubjectMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(NoSubjectMutationRule)} is not a valid rule source:
- Method mutate() is not a valid rule method: A method annotated with @Mutate must have at least one parameter"""
    }

    static class RuleWithEmptyInputPath extends RuleSource {
        @Model
        String create(@Path("") String thing) {}
    }

    def "path of rule input cannot be empty"() {
        when:
        extract(RuleWithEmptyInputPath)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(RuleWithEmptyInputPath)} is not a valid rule source:
- Method create(java.lang.String) is not a valid rule method: The declared model element path '' used for parameter 1 is not a valid path: Cannot use an empty string as a model path."""
    }

    static class RuleWithInvalidInputPath extends RuleSource {
        @Model
        String create(@Path("!!!!") String thing) {}
    }

    def "path of rule input has to be valid"() {
        when:
        extract(RuleWithInvalidInputPath)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(RuleWithInvalidInputPath)} is not a valid rule source:
- Method create(java.lang.String) is not a valid rule method: The declared model element path '!!!!' used for parameter 1 is not a valid path: Model element name '!!!!' has illegal first character '!' (names must start with an ASCII letter or underscore)."""
    }

    static class MutationRules extends RuleSource {
        @Mutate
        static void mutate1(List<String> strings) {
        }
    }

    def "mutation rules are registered"() {
        given:
        def registry = Mock(ModelRegistry)

        when:
        extractor.extract(MutationRules).apply(registry, node())

        then:
        1 * registry.configure(ModelActionRole.Mutate, _) >> { ModelActionRole role, ModelAction action ->
            assert action.descriptor.toString() == 'ModelRuleExtractorTest.MutationRules#mutate1(List<String>)'
        }
        0 * registry._
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

    def "finalize rules are registered"() {
        given:
        def registry = Mock(ModelRegistry)

        when:
        extractor.extract(MutationAndFinalizeRules).apply(registry, node())

        then:
        1 * registry.configure(ModelActionRole.Finalize, _) >> { ModelActionRole role, ModelAction action ->
            assert action.descriptor.toString() == 'ModelRuleExtractorTest.MutationAndFinalizeRules#finalize1(List<String>)'
        }
    }

    def "methods are processed ordered by their to string representation"() {
        given:
        def registry = Mock(ModelRegistry)
        def node = node()

        when:
        extractor.extract(MutationAndFinalizeRules).apply(registry, node)

        then:
        1 * registry.configure(ModelActionRole.Finalize, _) >> { ModelActionRole role, ModelAction action ->
            assert action.descriptor.toString() == 'ModelRuleExtractorTest.MutationAndFinalizeRules#finalize1(List<String>)'
        }

        then:
        1 * registry.configure(ModelActionRole.Mutate, _) >> { ModelActionRole role, ModelAction action ->
            assert action.descriptor.toString() == 'ModelRuleExtractorTest.MutationAndFinalizeRules#mutate1(List<String>)'
        }

        then:
        1 * registry.configure(ModelActionRole.Mutate, _) >> { ModelActionRole role, ModelAction action ->
            assert action.descriptor.toString() == 'ModelRuleExtractorTest.MutationAndFinalizeRules#mutate3(List<Integer>)'
        }
        0 * registry._
    }

    static class InvalidModelNameViaAnnotation extends RuleSource {
        @Model(" ")
        String foo() {
            "foo"
        }
    }

    def "invalid model name is not allowed"() {
        when:
        extract(InvalidModelNameViaAnnotation)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(InvalidModelNameViaAnnotation)} is not a valid rule source:
- Method foo() is not a valid rule method: The declared model element path ' ' is not a valid path: Model element name ' ' has illegal first character ' ' (names must start with an ASCII letter or underscore)."""
    }

    static class RuleSourceCreatingARawModelMap extends RuleSource {
        @Model
        void bar(ModelMap foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be a valid managed type"() {
        when:
        extract(RuleSourceCreatingARawModelMap)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == """Type ${fullyQualifiedNameOf(RuleSourceCreatingARawModelMap)} is not a valid rule source:
- Method bar(org.gradle.model.ModelMap) is not a valid rule method: Raw type org.gradle.model.ModelMap used for parameter 1 (all type parameters must be specified of parameterized type)"""
    }

    static class RuleSourceWithAVoidReturningNoArgumentMethod extends RuleSource {
        @Model
        void bar() {
        }
    }

    def "void returning model definition has to take at least one argument"() {
        when:
        extract(RuleSourceWithAVoidReturningNoArgumentMethod)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == """Type ${fullyQualifiedNameOf(RuleSourceWithAVoidReturningNoArgumentMethod)} is not a valid rule source:
- Method bar() is not a valid rule method: A method annotated with @Model must either take at least one parameter or have a non-void return type"""
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

    def "void returning model definition with for a type with a nested property of invalid managed type - #inspected.simpleName"() {
        when:
        extract(inspected)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ModelRuleExtractorTest.$inspected.simpleName#bar($managedType.simpleName) is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == """Type $ModelMap.name<?> is not a valid model element type:
- type parameter of org.gradle.model.ModelMap cannot be a wildcard.

The type was analyzed due to the following dependencies:
${fullyQualifiedNameOf(managedType)}
  \\--- property 'managedWithNestedInvalidManagedType' (${fullyQualifiedNameOf(nestedManagedType)})
    \\--- property 'invalidManaged' ($ModelMap.name<?>)"""

        where:
        inspected                                                        | managedType                                    | nestedManagedType
        RuleSourceCreatingManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithPropertyOfInvalidManagedType
        RuleSourceCreatingManagedWithNestedReferenceOfInvalidManagedType | ManagedWithNestedReferenceOfInvalidManagedType | ManagedWithReferenceOfInvalidManagedType
    }

    static class RuleSourceCreatingManagedWithNonManageableParent extends RuleSource {
        @Model
        void bar(ManagedWithNonManageableParents foo) {
        }
    }

    def "error message produced when super type is not a manageable type indicates the original (sub) type"() {
        when:
        extract(RuleSourceCreatingManagedWithNonManageableParent)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ModelRuleExtractorTest.RuleSourceCreatingManagedWithNonManageableParent#bar(ManagedWithNonManageableParents) is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == """Type $ModelMap.name<?> is not a valid model element type:
- type parameter of org.gradle.model.ModelMap cannot be a wildcard.

The type was analyzed due to the following dependencies:
${fullyQualifiedNameOf(ManagedWithNonManageableParents)}
  \\--- property 'invalidManaged' declared by ${fullyQualifiedNameOf(AnotherManagedWithPropertyOfInvalidManagedType)}, ${fullyQualifiedNameOf(ManagedWithPropertyOfInvalidManagedType)} ($ModelMap.name<?>)"""

        where:
        invalidTypeName = "$ParameterizedManaged.name<$String.name>"
    }

    static class HasRuleWithUncheckedModelMap extends RuleSource {
        @Model
        static ModelThing modelPath(ModelMap foo) {
            new ModelThing("foo")
        }
    }

    def "error when trying to use model map without specifying type param"() {
        when:
        extract(HasRuleWithUncheckedModelMap)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == """Type ${fullyQualifiedNameOf(HasRuleWithUncheckedModelMap)} is not a valid rule source:
- Method modelPath(org.gradle.model.ModelMap) is not a valid rule method: Raw type org.gradle.model.ModelMap used for parameter 1 (all type parameters must be specified of parameterized type)"""
    }

    static class NotEverythingAnnotated extends RuleSource {
        void mutate(String thing) {}

        private void ok() {}
    }

    def "all non-private methods must be annotated"() {
        when:
        extract(NotEverythingAnnotated)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(NotEverythingAnnotated)} is not a valid rule source:
- Method mutate(java.lang.String) is not a valid rule method: A method that is not annotated as a rule must be private"""
    }

    static class PrivateAnnotated extends RuleSource {
        @Mutate
        private void notOk(String subject) {}
    }

    def "no private methods may be annotated"() {
        when:
        extract(PrivateAnnotated)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(PrivateAnnotated)} is not a valid rule source:
- Method notOk(java.lang.String) is not a valid rule method: A rule method cannot be private"""
    }

    static class SeveralProblems {
        private String field1
        private String field2

        @Mutate
        private <T> void notOk() {}

        public void notARule() {}

        @Mutate
        @Validate
        private <T> String multipleProblems(@Path('') List list, @Path(':)') T value) {
            "broken"
        }

        @Model(":)")
        void thing() {
        }
    }

    def "collects all validation problems"() {
        when:
        extract(SeveralProblems)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == '''Type org.gradle.model.internal.inspect.ModelRuleExtractorTest.SeveralProblems is not a valid rule source:
- Rule source classes must directly extend org.gradle.model.RuleSource
- Field field1 is not valid: Fields must be static final.
- Field field2 is not valid: Fields must be static final.
- Method multipleProblems(java.util.List, T) is not a valid rule method: Can only be one of [annotated with @Model and returning a model element, annotated with @Model and taking a managed model element, annotated with @Defaults, annotated with @Mutate, annotated with @Finalize, annotated with @Validate, annotated with @Rules]
- Method multipleProblems(java.util.List, T) is not a valid rule method: A rule method cannot be private
- Method multipleProblems(java.util.List, T) is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)
- Method multipleProblems(java.util.List, T) is not a valid rule method: Raw type java.util.List used for parameter 1 (all type parameters must be specified of parameterized type)
- Method multipleProblems(java.util.List, T) is not a valid rule method: The declared model element path '' used for parameter 1 is not a valid path: Cannot use an empty string as a model path.
- Method multipleProblems(java.util.List, T) is not a valid rule method: The declared model element path ':)' used for parameter 2 is not a valid path: Model element name ':)' has illegal first character ':' (names must start with an ASCII letter or underscore).
- Method notOk() is not a valid rule method: A rule method cannot be private
- Method notOk() is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)
- Method notOk() is not a valid rule method: A method annotated with @Mutate must have at least one parameter
- Method notARule() is not a valid rule method: A method that is not annotated as a rule must be private
- Method thing() is not a valid rule method: The declared model element path ':)' is not a valid path: Model element name ':)' has illegal first character ':' (names must start with an ASCII letter or underscore).
- Method thing() is not a valid rule method: A method annotated with @Model must either take at least one parameter or have a non-void return type'''
    }

    static class RuleSourceWithDependencies extends RuleSource {
        @Mutate
        void method1(Long l) { }
        @Mutate
        void method2(String s) { }
    }

    def "rule method can imply plugin dependency"() {
        def ruleExtractor = Stub(MethodModelRuleExtractor)
        def extractor = new ModelRuleExtractor([ruleExtractor], proxyFactory, schemaStore, structBindingsStore)

        given:
        ruleExtractor.isSatisfiedBy(_) >> { MethodRuleDefinition method -> method.isAnnotationPresent(Mutate) }
        ruleExtractor.registration(_, _) >> { MethodRuleDefinition method, MethodModelRuleExtractionContext context ->
            return Stub(ExtractedModelRule) {
                getRuleDependencies() >> [method.getSubjectReference().getType().getConcreteClass()]
            }
        }

        expect:
        extractor.extract(RuleSourceWithDependencies).getRequiredPlugins() == [Long.class, String.class]
    }

    def "can assert no plugin dependencies"() {
        def ruleExtractor = Stub(MethodModelRuleExtractor)
        def extractor = new ModelRuleExtractor([ruleExtractor], proxyFactory, schemaStore, structBindingsStore)

        given:
        ruleExtractor.isSatisfiedBy(_) >> { MethodRuleDefinition method -> method.isAnnotationPresent(Mutate) }
        ruleExtractor.registration(_, _) >> { MethodRuleDefinition method, MethodModelRuleExtractionContext context ->
            return Stub(ExtractedModelRule) {
                getDescriptor() >> method.getDescriptor()
                getRuleDependencies() >> [method.getSubjectReference().getType().getConcreteClass()]
            }
        }

        when:
        extractor.extract(RuleSourceWithDependencies).assertNoPlugins()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "ModelRuleExtractorTest.RuleSourceWithDependencies#method1(Long) has dependencies on plugins: [class java.lang.Long]. Plugin dependencies are not supported in this context."
    }

    def "extracted stateless rules are cached"() {
        when:
        def fromFirstExtraction = extractor.extract(MutationRules)
        def fromSecondExtraction = extractor.extract(MutationRules)

        then:
        fromFirstExtraction.is(fromSecondExtraction)
    }

    def "extracted stateless abstract rules are cached"() {
        when:
        def fromFirstExtraction = extractor.extract(AbstractRules)
        def fromSecondExtraction = extractor.extract(AbstractRules)

        then:
        fromFirstExtraction.is(fromSecondExtraction)
    }

    def "new instance is created for extracted stateful abstract rules"() {
        when:
        def fromFirstExtraction = extractor.extract(AbstractPropertyRules)
        def fromSecondExtraction = extractor.extract(AbstractPropertyRules)

        then:
        !fromFirstExtraction.is(fromSecondExtraction)
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

    static class InvalidEachAnnotation extends RuleSource {
        @Mutate
        void mutate(String value, @Each Integer input) {}
    }

    def "invalid @Each annotations are not allowed"() {
        when:
        extract InvalidEachAnnotation

        then:
        def e = thrown InvalidModelRuleDeclarationException
        e.message == """Type ${fullyQualifiedNameOf(InvalidEachAnnotation)} is not a valid rule source:
- Method mutate(java.lang.String, java.lang.Integer) is not a valid rule method: Rule parameter #2 should not be annotated with @Each."""
    }

    static class InvalidEachAndPathAnnotation extends RuleSource {
        @Mutate
        void valid(@Path("value") String value, Integer input) {}

        @Mutate
        void invalid(@Each @Path("value") String value, Integer input) {}
    }

    def "both @Each and @Path annotations are not allowed"() {
        when:
        extract InvalidEachAndPathAnnotation

        then:
        def e = thrown InvalidModelRuleDeclarationException
        e.message == """Type ${fullyQualifiedNameOf(InvalidEachAndPathAnnotation)} is not a valid rule source:
- Method invalid(java.lang.String, java.lang.Integer) is not a valid rule method: Rule subject must not be annotated with both @Path and @Each."""
    }

    private void forcefullyClearReferences(Class<?> clazz) {
        ModelStoreTestUtils.removeClassFromGlobalClassSet(clazz)

        // Remove soft references
        Introspector.flushFromCaches(clazz)
    }

    MutableModelNode node() {
        return Stub(MutableModelNode) {
            getPath() >> ModelPath.ROOT
        }
    }
}

