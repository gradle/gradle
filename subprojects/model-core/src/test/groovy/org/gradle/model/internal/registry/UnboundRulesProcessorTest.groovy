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

import org.gradle.api.Transformer
import org.gradle.internal.Transformers
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.report.unbound.UnboundRule
import org.gradle.model.internal.report.unbound.UnboundRuleInput
import org.gradle.model.internal.report.unbound.UnboundRulesReporter
import org.gradle.util.ConfigureUtil
import spock.lang.Specification

import static org.gradle.util.TextUtil.normaliseLineSeparators

class UnboundRulesProcessorTest extends Specification {

    List<RuleBinder<?>> binders = []

    Transformer<List<ModelPath>, ModelPath> suggestionProvider = Transformers.constant([])

    String getReportForProcessedBinders() {
        reportFor(new UnboundRulesProcessor(binders, suggestionProvider).process())
    }

    void binder(@DelegatesTo(RuleBinderTestBuilder) Closure config) {
        binders << ConfigureUtil.configure(config, new RuleBinderTestBuilder()).build()
    }

    String reportFor(UnboundRule.Builder... rules) {
        reportFor(rules.toList()*.build())
    }

    String reportFor(List<UnboundRule> rules) {
        def writer = new StringWriter()
        new UnboundRulesReporter(new PrintWriter(writer), "").reportOn(rules)
        normaliseLineSeparators(writer.toString())
    }

    def "creates unbound rules for unfulfilled binders with unbound subject reference"() {
        binder {
            descriptor("ruleWithUnboundSubjectReference")
            subjectReference("path.subject", String)
        }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("ruleWithUnboundSubjectReference")
                        .mutableInput(UnboundRuleInput.type(String).path("path.subject"))
        )
    }

    def "creates unbound rules for multiple binders"() {
        binder {
            descriptor("firstRule")
            subjectReference("path.subject.first", String)
        }
        binder {
            descriptor("secondRule")
            subjectReference("path.subject.second", Number)
        }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("firstRule")
                        .mutableInput(UnboundRuleInput.type(String).path("path.subject.first")),
                UnboundRule.descriptor("secondRule")
                        .mutableInput(UnboundRuleInput.type(Number).path("path.subject.second"))
        )
    }

    def "creates unbound rules for unfulfilled binders with unbound input references"() {
        binder {
            descriptor("ruleWithUnboundInputReferences")
            inputReference("reference.first", String)
            inputReference("reference.second", Number)
        }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("ruleWithUnboundInputReferences")
                        .immutableInput(UnboundRuleInput.type(String).path("reference.first"))
                        .immutableInput(UnboundRuleInput.type(Number).path("reference.second"))
        )
    }

    def "creates unbound rules for unfulfilled binders with pathless references"() {
        binder {
            descriptor("ruleWithUnboundPathlessReferences")
            subjectReference(Number)
            inputReference(String)
        }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("ruleWithUnboundPathlessReferences")
                        .mutableInput(UnboundRuleInput.type(Number))
                        .immutableInput(UnboundRuleInput.type(String))
        )
    }

    def "creates unbound rules for unfulfilled binders with bound subject reference and partially bound input references"() {
        binder {
            descriptor("partiallyBoundRule")
            subjectReference("subject", Number)
            inputReference("reference.first", Number)
            inputReference(String)
            inputReference("reference.third", Boolean)
            bindSubjectReference("subject")
            bindInputReference(0, "reference.first")
            bindInputReference(1, "reference.second")
        }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("partiallyBoundRule")
                        .mutableInput(UnboundRuleInput.type(Number).path("subject").bound())
                        .immutableInput(UnboundRuleInput.type(Number).path("reference.first").bound())
                        .immutableInput(UnboundRuleInput.type(String).path("reference.second").bound())
                        .immutableInput(UnboundRuleInput.type(Boolean).path("reference.third"))
        )
    }

    def "creates unbound rules with suggestions"() {
        binder {
            descriptor("ruleWithSuggestions")
            subjectReference("subject", Number)
            inputReference(String)
            inputReference("input.second", Boolean)
            inputReference("input.third", Long)
            bindInputReference(2, "input.third")
        }

        setSuggestionProvider { [it] }

        expect:
        reportForProcessedBinders == reportFor(
                UnboundRule.descriptor("ruleWithSuggestions")
                        .mutableInput(UnboundRuleInput.type(Number).path("subject").suggestions("subject"))
                        .immutableInput(UnboundRuleInput.type(String))
                        .immutableInput(UnboundRuleInput.type(Boolean).path("input.second").suggestions("input.second"))
                        .immutableInput(UnboundRuleInput.type(Long).path("input.third").bound())
        )
    }

    private class RuleBinderTestBuilder {

        private ModelRuleDescriptor descriptor
        private ModelReference<?> subjectReference
        private String subjectReferenceBindingPath
        private List<ModelReference<?>> inputReferences = []
        private Map<Integer, String> boundInputReferencePaths = [:]

        void subjectReference(Class type) {
            subjectReference = ModelReference.of(ModelType.of(type))
        }

        void subjectReference(String path, Class type) {
            subjectReference = ModelReference.of(new ModelPath(path), ModelType.of(type))
        }

        void bindSubjectReference(String path) {
            subjectReferenceBindingPath = path
        }

        void inputReference(Class type) {
            inputReferences.add(ModelReference.of(ModelType.of(type)))
        }

        void inputReference(String path, Class type) {
            inputReferences.add(ModelReference.of(new ModelPath(path), ModelType.of(type)))
        }

        void bindInputReference(int index, String path) {
            boundInputReferencePaths[index] = path
        }

        void descriptor(String descriptor) {
            this.descriptor = new SimpleModelRuleDescriptor(descriptor)
        }

        RuleBinder build() {
            def binder = new RuleBinder(subjectReference, inputReferences, descriptor, null)
            if (subjectReferenceBindingPath) {
                binder.bindSubject(new ModelPath(subjectReferenceBindingPath))
            }
            boundInputReferencePaths.each { index, path ->
                binder.bindInput(index, new ModelPath(path))
            }
            return binder
        }
    }
}
