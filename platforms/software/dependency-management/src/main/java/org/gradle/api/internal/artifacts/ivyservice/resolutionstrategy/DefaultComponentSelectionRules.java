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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.rules.DefaultRuleActionAdapter;
import org.gradle.internal.rules.DefaultRuleActionValidator;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.RuleActionAdapter;
import org.gradle.internal.rules.RuleActionValidator;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultComponentSelectionRules implements ComponentSelectionRulesInternal {
    private static final String INVALID_SPEC_ERROR = "Could not add a component selection rule for module '%s'.";

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private Set<SpecRuleAction<? super ComponentSelection>> rules;

    private final RuleActionAdapter ruleActionAdapter;
    private final NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser;

    public DefaultComponentSelectionRules(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this(moduleIdentifierFactory, createAdapter());
    }

    protected DefaultComponentSelectionRules(ImmutableModuleIdentifierFactory moduleIdentifierFactory, RuleActionAdapter ruleActionAdapter) {
        this.ruleActionAdapter = ruleActionAdapter;
        this.moduleIdentifierNotationParser = NotationParserBuilder
            .toType(ModuleIdentifier.class)
            .fromCharSequence(new ModuleIdentifierNotationConverter(moduleIdentifierFactory))
            .toComposite();
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
    public void setMutationValidator(MutationValidator mutationValidator) {
        this.mutationValidator = mutationValidator;
    }

    private static RuleActionAdapter createAdapter() {
        RuleActionValidator ruleActionValidator = new DefaultRuleActionValidator();
        return new DefaultRuleActionAdapter(ruleActionValidator, "ComponentSelectionRules");
    }

    @Override
    public Collection<SpecRuleAction<? super ComponentSelection>> getRules() {
        return rules != null ? rules : Collections.emptySet();
    }

    @Override
    public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromAction(selectionAction)));
    }

    @Override
    public ComponentSelectionRules all(Closure<?> closure) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromClosure(ComponentSelection.class, closure)));
    }

    @Override
    public ComponentSelectionRules all(Object ruleSource) {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromRuleSource(ComponentSelection.class, ruleSource)));
    }

    @Override
    public ComponentSelectionRules withModule(Object id, Action<? super ComponentSelection> selectionAction) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromAction(selectionAction)));
    }

    @Override
    public ComponentSelectionRules withModule(Object id, Closure<?> closure) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromClosure(ComponentSelection.class, closure)));
    }

    @Override
    public ComponentSelectionRules withModule(Object id, Object ruleSource) {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromRuleSource(ComponentSelection.class, ruleSource)));
    }

    @Override
    public ComponentSelectionRules addRule(SpecRuleAction<? super ComponentSelection> specRuleAction) {
        mutationValidator.validateMutation(STRATEGY);
        if (rules == null) {
            rules = new LinkedHashSet<>();
        }
        rules.add(specRuleAction);
        return this;
    }

    @Override
    public ComponentSelectionRules addRule(RuleAction<? super ComponentSelection> specRuleAction) {
        return addRule(createAllSpecRulesAction(specRuleAction));
    }

    private SpecRuleAction<? super ComponentSelection> createSpecRuleActionFromId(Object id, RuleAction<? super ComponentSelection> ruleAction) {
        final ModuleIdentifier moduleIdentifier;

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id);
        } catch (UnsupportedNotationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, id == null ? "null" : id.toString()), e);
        }

        Spec<ComponentSelection> spec = new ComponentSelectionMatchingSpec(moduleIdentifier);
        return new SpecRuleAction<>(ruleAction, spec);
    }

    private SpecRuleAction<? super ComponentSelection> createAllSpecRulesAction(RuleAction<? super ComponentSelection> ruleAction) {
        return new SpecRuleAction<>(ruleAction, Specs.satisfyAll());
    }

    static class ComponentSelectionMatchingSpec implements Spec<ComponentSelection> {
        final ModuleIdentifier target;

        private ComponentSelectionMatchingSpec(ModuleIdentifier target) {
            this.target = target;
        }

        @Override
        public boolean isSatisfiedBy(ComponentSelection selection) {
            return selection.getCandidate().getGroup().equals(target.getGroup()) && selection.getCandidate().getModule().equals(target.getName());
        }
    }
}
