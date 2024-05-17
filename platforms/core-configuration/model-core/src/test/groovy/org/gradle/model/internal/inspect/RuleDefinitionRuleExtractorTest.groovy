/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.registry.DefaultModelRegistry

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class RuleDefinitionRuleExtractorTest extends ProjectRegistrySpec {
    def extractor = new ModelRuleExtractor([new RuleDefinitionRuleExtractor()], proxyFactory, schemaStore, structBindingsStore)

    static class InvalidSignature extends RuleSource {
        @Rules
        void broken1(String string, RuleSource ruleSource) {
        }

        @Rules
        void broken2() {
        }

        @Rules
        String broken3(String string) {
            "broken"
        }
    }

    def "rule method must have first parameter that is assignable to RuleSource and have void return type"() {
        when:
        extractor.extract(InvalidSignature)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${fullyQualifiedNameOf(InvalidSignature)} is not a valid rule source:
- Method broken3(java.lang.String) is not a valid rule method: A method annotated with @Rules must have void return type.
- Method broken3(java.lang.String) is not a valid rule method: A method annotated with @Rules must have at least two parameters
- Method broken1(java.lang.String, ${RuleSource.name}) is not a valid rule method: The first parameter of a method annotated with @Rules must be a subtype of ${RuleSource.name}
- Method broken2() is not a valid rule method: A method annotated with @Rules must have at least two parameters"""
    }

    static class SomeRuleSource extends RuleSource {}

    static class InvalidEachAnnotationOnParameter extends RuleSource {
        @Rules
        void input(SomeRuleSource rules, String value, @Each Integer input) {}
    }

    def "invalid @Each annotations on parameters are not allowed"() {
        when:
        extractor.extract InvalidEachAnnotationOnParameter

        then:
        def e = thrown InvalidModelRuleDeclarationException
        e.message == """Type ${fullyQualifiedNameOf(InvalidEachAnnotationOnParameter)} is not a valid rule source:
- Method input($SomeRuleSource.name, java.lang.String, java.lang.Integer) is not a valid rule method: Rule parameter #3 should not be annotated with @Each."""
    }


    static class InvalidEachAnnotationOnRuleSource extends RuleSource {
        @Rules
        void rules(@Each SomeRuleSource rules, String value, Integer input) {}
    }

    def "invalid @Each annotations on rule sources are not allowed"() {
        when:
        extractor.extract InvalidEachAnnotationOnRuleSource

        then:
        def e = thrown InvalidModelRuleDeclarationException
        e.message == """Type ${fullyQualifiedNameOf(InvalidEachAnnotationOnRuleSource)} is not a valid rule source:
- Method rules($SomeRuleSource.name, java.lang.String, java.lang.Integer) is not a valid rule method: Rule parameter #1 should not be annotated with @Each."""
    }

    static class InvalidEachAndPathAnnotation extends RuleSource {
        @Rules
        void valid(SomeRuleSource rules, @Path("value") String value, Integer input) {}

        @Rules
        void invalid(SomeRuleSource rules, @Each @Path("value") String value, Integer input) {}
    }

    def "both @Each and @Path annotations are not allowed"() {
        when:
        extractor.extract InvalidEachAndPathAnnotation

        then:
        def e = thrown InvalidModelRuleDeclarationException
        e.message == """Type ${fullyQualifiedNameOf(InvalidEachAndPathAnnotation)} is not a valid rule source:
- Method invalid($SomeRuleSource.name, java.lang.String, java.lang.Integer) is not a valid rule method: Rule subject must not be annotated with both @Path and @Each."""
    }

    static class Bean {
        String value
    }

    static class RuleSourceWithParameter extends RuleSource {
        @Rules
        void methodWithParameters(SomeRuleSource rules, Bean subject, Integer input) {
            subject.value = "input: " + input
        }
    }

    def "extracts input parameters"() {
        given:
        registry.registerInstance("input", 12)
        registry.registerInstance("item", new Bean())
        def node = ((DefaultModelRegistry) registry).node("item")

        when:
        def ruleSource = extractor.extract RuleSourceWithParameter
        ruleSource.apply(registry, node)
        def item = registry.realize("item", Bean)

        then:
        item.value == "input: 12"
    }
}
