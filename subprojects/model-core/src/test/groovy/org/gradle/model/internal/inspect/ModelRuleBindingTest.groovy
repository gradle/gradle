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

import org.gradle.internal.reflect.Instantiator
import org.gradle.model.*
import org.gradle.model.internal.core.UnmanagedModelProjection
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.report.AmbiguousBindingReporter
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Test the binding of rules by the registry.
 */
class ModelRuleBindingTest extends Specification {

    final static Instantiator UNUSED_INSTANTIATOR = null

    def modelRegistry = new DefaultModelRegistry()
    def inspector = new ModelRuleInspector(MethodRuleDefinitionHandlers.coreHandlers(UNUSED_INSTANTIATOR, new DefaultModelSchemaStore()))

    static class AmbiguousBindingsInOneSource {
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
        inspector.inspect(AmbiguousBindingsInOneSource, modelRegistry, {})

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == MethodModelRuleDescriptor.of(AmbiguousBindingsInOneSource, "m").toString()
        def cause = e.cause as ModelRuleBindingException
        def message = new AmbiguousBindingReporter(String.name, "parameter 1", [
                new AmbiguousBindingReporter.Provider("s2", MethodModelRuleDescriptor.of(AmbiguousBindingsInOneSource, "s2").toString()),
                new AmbiguousBindingReporter.Provider("s1", MethodModelRuleDescriptor.of(AmbiguousBindingsInOneSource, "s1").toString()),
        ]).asString()

        cause.message == message
    }

    static class ProvidesStringOne {
        @Model
        String s1() {
            "foo"
        }
    }

    static class ProvidesStringTwo {
        @Model
        String s2() {
            "bar"
        }
    }

    static class MutatesString {
        @Mutate
        void m(String s) {

        }
    }

    @Unroll
    def "ambiguous binding is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            inspector.inspect(it, modelRegistry, {})
        }

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == MethodModelRuleDescriptor.of(MutatesString, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new AmbiguousBindingReporter(String.name, "parameter 1", [
                new AmbiguousBindingReporter.Provider("s2", MethodModelRuleDescriptor.of(ProvidesStringTwo, "s2").toString()),
                new AmbiguousBindingReporter.Provider("s1", MethodModelRuleDescriptor.of(ProvidesStringOne, "s1").toString()),
        ]).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, ProvidesStringTwo, MutatesString].permutations()
    }

    static class MutatesS1AsInteger {
        @Mutate
        void m(@Path("s1") Integer s1) {

        }
    }

    @Unroll
    def "incompatible writable type binding of mutate rule is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            inspector.inspect(it, modelRegistry, {})
        }

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == MethodModelRuleDescriptor.of(MutatesS1AsInteger, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new IncompatibleTypeReferenceReporter(
                MethodModelRuleDescriptor.of(ProvidesStringOne, "s1").toString(),
                "s1",
                Integer.name,
                "parameter 1",
                true,
                [UnmanagedModelProjection.description(ModelType.of(String))]
        ).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, MutatesS1AsInteger].permutations()
    }

    static class ReadS1AsInteger {
        @Mutate
        void m(Integer unbound, @Path("s1") Integer s1) {

        }
    }

    @Unroll
    def "incompatible readable type binding of mutate rule is detected irrespective of discovery order - #order.simpleName"() {
        when:
        order.each {
            inspector.inspect(it, modelRegistry, {})
        }

        then:
        def e = thrown(InvalidModelRuleException)
        e.descriptor == MethodModelRuleDescriptor.of(ReadS1AsInteger, "m").toString()

        def cause = e.cause as ModelRuleBindingException
        def message = new IncompatibleTypeReferenceReporter(
                MethodModelRuleDescriptor.of(ProvidesStringOne, "s1").toString(),
                "s1",
                Integer.name,
                "parameter 2",
                false,
                [UnmanagedModelProjection.description(ModelType.of(String))]
        ).asString()

        cause.message == message

        where:
        order << [ProvidesStringOne, ReadS1AsInteger].permutations()
    }

}
