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

package org.gradle.internal.rules;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.model.internal.type.ModelType;

public class DefaultRuleActionAdapter<T> implements RuleActionAdapter<T> {
    private static final String INVALID_CLOSURE_ERROR = "The closure provided is not valid as a rule for '%s'.";
    private static final String INVALID_ACTION_ERROR = "The action provided is not valid as a rule for '%s'.";
    private static final String INVALID_RULE_SOURCE_ERROR = "The rule source provided does not provide a valid rule for '%s'.";

    private final RuleActionValidator<T> ruleActionValidator;
    private final String context;

    public DefaultRuleActionAdapter(RuleActionValidator<T> ruleActionValidator, String context) {
        this.ruleActionValidator = ruleActionValidator;
        this.context = context;
    }

    public RuleAction<? super T> createFromClosure(Class<T> subjectType, Closure<?> closure) {
        try {
            return ruleActionValidator.validate(new ClosureBackedRuleAction<T>(subjectType, closure));
        } catch (RuleActionValidationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_CLOSURE_ERROR, context), e);
        }
    }

    public RuleAction<? super T> createFromAction(Action<? super T> action) {
        try {
            return ruleActionValidator.validate(new NoInputsRuleAction<T>(action));
        } catch (RuleActionValidationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_ACTION_ERROR, context), e);
        }
    }

    public RuleAction<? super T> createFromRuleSource(Class<T> subjectType, Object ruleSource) {
        try {
            return ruleActionValidator.validate(RuleSourceBackedRuleAction.create(ModelType.of(subjectType), ruleSource));
        } catch (RuleActionValidationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_RULE_SOURCE_ERROR, context), e);
        }
    }
}
