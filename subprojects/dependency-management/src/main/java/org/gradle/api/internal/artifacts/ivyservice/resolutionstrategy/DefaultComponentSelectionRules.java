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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.internal.rules.*;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.notations.ModuleIdentiferNotationParser;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import java.util.Collection;
import java.util.Set;

public class DefaultComponentSelectionRules implements ComponentSelectionRulesInternal {
    private static final String INVALID_SPEC_ERROR = "Could not add a component selection rule for module '%s'.";

    private final Set<SpecRuleAction<? super ComponentSelection>> rules = Sets.newLinkedHashSet();

    private final RuleActionAdapter<ComponentSelection> ruleActionAdapter;
    private final NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser;

    public DefaultComponentSelectionRules() {
        this(createAdapter(), createModuleIdentifierNotationParser());
    }

    protected DefaultComponentSelectionRules(RuleActionAdapter<ComponentSelection> ruleActionAdapter, NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser) {
        this.ruleActionAdapter = ruleActionAdapter;
        this.moduleIdentifierNotationParser = moduleIdentifierNotationParser;
    }

    private static NotationParser<Object, ModuleIdentifier> createModuleIdentifierNotationParser() {
        return NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .parser(new ModuleIdentiferNotationParser())
                .toComposite();
    }

    private static RuleActionAdapter<ComponentSelection> createAdapter() {
        RuleActionValidator<ComponentSelection> ruleActionValidator = new DefaultRuleActionValidator<ComponentSelection>(Lists.newArrayList(ComponentMetadata.class, IvyModuleDescriptor.class));
        return new DefaultRuleActionAdapter<ComponentSelection>(ruleActionValidator, ComponentSelectionRules.class.getSimpleName());
    }

    public Collection<SpecRuleAction<? super ComponentSelection>> getRules() {
        return rules;
    }

    public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromAction(selectionAction)));
    }

    public ComponentSelectionRules all(Closure<?> closure) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromClosure(ComponentSelection.class, closure)));
    }

    public ComponentSelectionRules all(Object ruleSource) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromRuleSource(ComponentSelection.class, ruleSource)));
    }

    public ComponentSelectionRules withModule(Object id, Action<? super ComponentSelection> selectionAction) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromAction(selectionAction)));
    }

    public ComponentSelectionRules withModule(Object id, Closure<?> closure) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromClosure(ComponentSelection.class, closure)));
    }

    public ComponentSelectionRules withModule(Object id, Object ruleSource) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromRuleSource(ComponentSelection.class, ruleSource)));
    }

    private ComponentSelectionRules addRule(SpecRuleAction<? super ComponentSelection> specRuleAction) {
        rules.add(specRuleAction);
        return this;
    }

    private SpecRuleAction<? super ComponentSelection> createSpecRuleActionFromId(Object id, RuleAction<? super ComponentSelection> ruleAction) {
        final ModuleIdentifier moduleIdentifier;

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id);
        } catch (UnsupportedNotationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, id == null ? "null" : id.toString()), e);
        }

        Spec<ComponentSelection> spec = new ComponentSelectionMatchingSpec(moduleIdentifier);
        return new SpecRuleAction<ComponentSelection>(ruleAction, spec);
    }

    private SpecRuleAction<? super ComponentSelection> createAllSpecRulesAction(RuleAction<? super ComponentSelection> ruleAction) {
        return new SpecRuleAction<ComponentSelection>(ruleAction, Specs.<ComponentSelection>satisfyAll());
    }

    static class ComponentSelectionMatchingSpec implements Spec<ComponentSelection> {
        final ModuleIdentifier target;

        private ComponentSelectionMatchingSpec(ModuleIdentifier target) {
            this.target = target;
        }

        public boolean isSatisfiedBy(ComponentSelection selection) {
            return selection.getCandidate().getGroup().equals(target.getGroup()) && selection.getCandidate().getModule().equals(target.getName());
        }
    }
}
