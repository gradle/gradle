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

package org.gradle.model.internal.registry

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.model.*
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.inspect.*

class ScopedRuleTest extends ProjectRegistrySpec {
    def extractors = [new DependencyAddingModelRuleExtractor()] + MethodModelRuleExtractors.coreExtractors(schemaStore)
    ModelRegistry registry = new ModelRegistryHelper(new ModelRuleExtractor(extractors, proxyFactory, schemaStore, structBindingsStore))

    static class RuleSourceUsingRuleWithDependencies extends RuleSource {
        @HasDependencies
        void rule() {}
    }

    class ImperativePlugin implements Plugin<Project> {
        void apply(Project target) {
        }
    }

    class DependencyAddingModelRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<HasDependencies> {
        @Override
        def <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext extractionContext) {
            new ExtractedModelRule() {
                @Override
                void apply(MethodModelRuleApplicationContext applicationContext, MutableModelNode target) {
                }

                @Override
                List<? extends Class<?>> getRuleDependencies() {
                    return [ImperativePlugin]
                }

                @Override
                MethodRuleDefinition<?, ?> getRuleDefinition() {
                    return ruleDefinition
                }
            }
        }
    }

    def "cannot apply a scoped rule that has dependencies"() {
        registry.registerInstance("values", "foo")
            .apply("values", RuleSourceUsingRuleWithDependencies)

        when:
        registry.get("values")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause.class == UnsupportedOperationException
        e.cause.message == "ScopedRuleTest.RuleSourceUsingRuleWithDependencies#rule() has dependencies on plugins: [$ImperativePlugin]. Plugin dependencies are not supported in this context."
    }

    static class CreatorRule extends RuleSource {
        @Model
        String string() {
            "foo"
        }
    }

    def "cannot apply registration rules in scope other than root"() {
        given:
        registry.registerInstance("values", "foo")
            .apply("values", CreatorRule)

        when:
        registry.get("values")

        then:
        ModelRuleExecutionException e = thrown()
        e.cause.class == InvalidModelRuleDeclarationException
        e.cause.message == "Rule ScopedRuleTest.CreatorRule#string() cannot be applied at the scope of model element values as creation rules cannot be used when applying rule sources to particular elements"
    }

    static class ByPathBoundInputsChildRule extends RuleSource {
        @Mutate
        void mutateFirst(@Path("first") MutableValue first) {
            first.value = "first"
        }

        @Mutate
        void mutateSecond(@Path("second") MutableValue second, @Path("first") MutableValue first) {
            second.value = "from first: $first.value"
        }
    }

    class MutableValue {
        String value
    }

    def "by-path bindings of scoped rules are bound to inner scope"() {
        given:
        registry.registerInstance("first", new MutableValue())
            .registerInstance("second", new MutableValue())
            .registerInstance("values", "foo")
            .apply("values", ByPathBoundInputsChildRule)
            .mutate {
            it.path "values" node {
                it.addLinkInstance("values.first", new MutableValue())
                it.addLinkInstance("values.second", new MutableValue())
            }
        }

        when:
        registry.get("values")

        then:
        registry.get("first", MutableValue).value == null
        registry.get("second", MutableValue).value == null
        registry.get("values.first", MutableValue).value == "first"
        registry.get("values.second", MutableValue).value == "from first: first"
    }

    static class ByTypeSubjectBoundToScopeChildRule extends RuleSource {
        @Mutate
        void mutateScopeChild(MutableValue value) {
            value.value = "foo"
        }
    }

    def "can bind subject by type to a child of rule scope"() {
        given:
        registry.registerInstance("values", "foo")
            .apply("values", ByTypeSubjectBoundToScopeChildRule)
            .mutate {
            it.path "values" node {
                it.addLinkInstance("values.mutable", new MutableValue())
            }
        }

        when:
        registry.get("values")

        then:
        registry.get("values.mutable", MutableValue).value == "foo"
    }

    static class ByTypeBindingSubjectRule extends RuleSource {
        @Mutate
        void connectElementToInput(MutableValue element, Integer input) {
            element.value = input
        }
    }

    def "by-type subject bindings are scoped to the scope of an inner rule"() {
        given:
        registry.registerInstance("element", new MutableValue())
            .registerInstance("input", 10)
            .registerInstance("values", "foo")
            .apply("values", ByTypeBindingSubjectRule)
            .mutate {
            it.path "values" node {
                it.addLinkInstance("values.element", new MutableValue())
            }
        }

        when:
        registry.get("values")

        then:
        registry.get("values.element", MutableValue).value == "10"
        registry.get("element", MutableValue).value == null
    }

    static class ByTypeBindingInputRule extends RuleSource {
        @Mutate
        void byTypeInputBindingRule(MutableValue inner, MutableValue outer) {
            inner.value = "from outer: $outer.value"
        }
    }

    def "by-type input bindings are scoped to the outer scope"() {
        given:
        registry.registerInstance("values", "foo")
            .apply("values", ByTypeBindingInputRule)
            .registerInstance("element", new MutableValue(value: "outer"))
            .mutate {
            it.path "values" node {
                it.addLinkInstance("values.element", new MutableValue())
            }
        }

        when:
        registry.get("values")

        then:
        registry.get("values.element", MutableValue).value == "from outer: outer"
    }
}
