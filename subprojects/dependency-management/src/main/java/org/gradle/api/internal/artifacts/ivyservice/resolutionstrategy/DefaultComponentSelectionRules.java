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
import org.gradle.api.Action;
import org.gradle.api.InvalidActionClosureException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.RuleAction;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.ClosureBackedRuleAction;
import org.gradle.api.internal.DelegatingTargetedRuleAction;
import org.gradle.api.internal.NoInputsRuleAction;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.notations.ModuleIdentiferNotationParser;
import org.gradle.api.specs.Spec;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultComponentSelectionRules implements ComponentSelectionRulesInternal {
    final Set<RuleAction<? super ComponentSelection>> rules = Sets.newLinkedHashSet();

    private final static List<Class<?>> VALID_INPUT_TYPES = Lists.newArrayList(ComponentMetadata.class, IvyModuleDescriptor.class);
    private final static List<Character> INVALID_SPEC_CHARS = Lists.newArrayList('*', '[', ']', '(', ')', ',', '+');

    private static final String UNSUPPORTED_PARAMETER_TYPE_ERROR = "Unsupported parameter type for component selection rule: %s";
    private static final String ILLEGAL_CHAR_SPEC_ERROR = "Illegal character '%s' found in module constraint.";
    private static final String INVALID_CLOSURE_ERROR = "The closure provided is not valid as a rule action for '%s'.";

    public Collection<RuleAction<? super ComponentSelection>> getRules() {
        return rules;
    }

    public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction) {
        addRule(new NoInputsRuleAction<ComponentSelection>(selectionAction));
        return this;
    }

    public ComponentSelectionRules all(RuleAction<? super ComponentSelection> ruleAction) {
        addRule(validateInputTypes(ruleAction));
        return this;
    }

    public ComponentSelectionRules all(Closure<?> closure) {
        addRule(createRuleActionFromClosure(closure));
        return this;
    }

    public ComponentSelectionRules module(String id, Action<? super ComponentSelection> selectionAction) {
        addRule(createTargetedRuleActionFromId(id, new NoInputsRuleAction<ComponentSelection>(selectionAction)));
        return this;
    }

    public ComponentSelectionRules module(String id, RuleAction<? super ComponentSelection> ruleAction) {
        addRule(createTargetedRuleActionFromId(id, validateInputTypes(ruleAction)));
        return this;
    }

    public ComponentSelectionRules module(String id, Closure<?> closure) {
        addRule(createTargetedRuleActionFromId(id, createRuleActionFromClosure(closure)));
        return this;
    }

    private void addRule(RuleAction<? super ComponentSelection> ruleAction) {
        rules.add(ruleAction);
    }

    private RuleAction<? super ComponentSelection> createRuleActionFromClosure(Closure<?> closure) {
        try {
            return validateInputTypes(new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection.class, closure));
        } catch (IllegalArgumentException e) {
            throw new InvalidActionClosureException(String.format(INVALID_CLOSURE_ERROR, ComponentSelectionRules.class.getSimpleName()), closure, e);
        }
    }

    private RuleAction<? super ComponentSelection> validateInputTypes(RuleAction<? super ComponentSelection> ruleAction) {
        for (Class<?> inputType : ruleAction.getInputTypes()) {
            if (!VALID_INPUT_TYPES.contains(inputType)) {
                throw new IllegalArgumentException(String.format(UNSUPPORTED_PARAMETER_TYPE_ERROR, inputType.getName()));
            }
        }
        return ruleAction;
    }

    private RuleAction<? super ComponentSelection> createTargetedRuleActionFromId(Object id, RuleAction<? super ComponentSelection> ruleAction) {
        final NotationParser<Object, ModuleIdentifier> parser = NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .parser(new ModuleIdentiferNotationParser())
                .toComposite();
        final ModuleIdentifier moduleIdentifier = parser.parseNotation(id);

        for (char c : INVALID_SPEC_CHARS) {
            if (moduleIdentifier.getGroup().indexOf(c) != -1 || moduleIdentifier.getName().indexOf(c) != -1) {
                throw new InvalidUserDataException(String.format(ILLEGAL_CHAR_SPEC_ERROR, c));
            }
        }

        Spec<ComponentSelection> spec = new ComponentSelectionMatchingSpec(moduleIdentifier.getGroup(), moduleIdentifier.getName());
        return new DelegatingTargetedRuleAction<ComponentSelection>(spec, ruleAction);
    }

    static class ComponentSelectionMatchingSpec implements Spec<ComponentSelection> {
        final String group;
        final String module;

        private ComponentSelectionMatchingSpec(String group, String module) {
            this.group = group;
            this.module = module;
        }

        public boolean isSatisfiedBy(ComponentSelection selection) {
            return selection.getCandidate().getGroup().equals(group) && selection.getCandidate().getModule().equals(module);
        }
    }
}
