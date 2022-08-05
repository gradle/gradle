/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.model.internal.core.TypeCompatibilityModelProjectionSupport
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.report.AmbiguousBindingReporter
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter
import org.gradle.model.internal.type.ModelType

/**
 * Test the binding of rules by the registry.
 */
class ModelRuleBindingTest extends ProjectRegistrySpec {
    def modelRegistry = new DefaultModelRegistry(modelRuleExtractor, null)

    static class AmbiguousBindingsInOneSource extends RuleSource {
        @Mutate
        void m(String s) {

        }

        @Model
        String s1() {
            "foo"
        }

        @Model
        String s2() {
            "bar"
        }
    }

    def "error message produced when unpathed reference matches more than one item"() {
        when:
        modelRegistry.getRoot().applyToSelf(AmbiguousBindingsInOneSource)
        modelRegistry.bindAllReferences()

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == methodDescriptor(AmbiguousBindingsInOneSource, "m").toString()
        def cause = e.cause as ModelRuleBindingException
        def message = new AmbiguousBindingReporter(String.name, "parameter 1", [
            new AmbiguousBindingReporter.Provider("s2", methodDescriptor(AmbiguousBindingsInOneSource, "s2").toString()),
            new AmbiguousBindingReporter.Provider("s1", methodDescriptor(AmbiguousBindingsInOneSource, "s1").toString()),
        ]).asString()

        cause.message == message
    }

    private ModelRuleDescriptor methodDescriptor(Class type, String methodName) {
        def declaringType = ModelType.of(type)
        def method = type.getDeclaredMethods().find { it.name == methodName }

        MethodModelRuleDescriptor.of(WeaklyTypeReferencingMethod.of(declaringType, ModelType.of(method.returnType), method))
    }

    static class ProvidesStringOne extends RuleSource {
        @Model
        String s1() {
            "foo"
        }
    }

    static class ProvidesStringTwo extends RuleSource {
        @Model
        String s2() {
            "bar"
        }
    }

    static class MutatesString extends RuleSource {
        @Mutate
        void m(String s) {

        }
    }

    def "ambiguous binding is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            modelRegistry.getRoot().applyToSelf(it)
        }
        modelRegistry.bindAllReferences()

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == methodDescriptor(MutatesString, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new AmbiguousBindingReporter(String.name, "parameter 1", [
            new AmbiguousBindingReporter.Provider("s2", methodDescriptor(ProvidesStringTwo, "s2").toString()),
            new AmbiguousBindingReporter.Provider("s1", methodDescriptor(ProvidesStringOne, "s1").toString()),
        ]).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, ProvidesStringTwo, MutatesString].permutations()
    }

    static class MutatesS1AsInteger extends RuleSource {
        @Mutate
        void m(@Path("s1") Integer s1) {

        }
    }

    def "incompatible writable type binding of mutate rule is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            modelRegistry.getRoot().applyToSelf(it)
        }
        modelRegistry.bindAllReferences()

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == methodDescriptor(MutatesS1AsInteger, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new IncompatibleTypeReferenceReporter(
            methodDescriptor(ProvidesStringOne, "s1").toString(),
            "s1",
            Integer.name,
            "parameter 1",
            true,
            [
                TypeCompatibilityModelProjectionSupport.description(ModelType.of(String)),
                TypeCompatibilityModelProjectionSupport.description(ModelType.of(ModelElement))
            ]
        ).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, MutatesS1AsInteger].permutations()
    }

    static class ReadS1AsInteger extends RuleSource {
        @Mutate
        void m(Integer unbound, @Path("s1") Integer s1) {

        }
    }

    def "incompatible readable type binding of mutate rule is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            modelRegistry.getRoot().applyToSelf(it)
        }
        modelRegistry.bindAllReferences()

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == methodDescriptor(ReadS1AsInteger, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new IncompatibleTypeReferenceReporter(
            methodDescriptor(ProvidesStringOne, "s1").toString(),
            "s1",
            Integer.name,
            "parameter 2",
            false,
            [
                TypeCompatibilityModelProjectionSupport.description(ModelType.of(String)),
                TypeCompatibilityModelProjectionSupport.description(ModelType.of(ModelElement))
            ]
        ).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, ReadS1AsInteger].permutations()
    }

}
