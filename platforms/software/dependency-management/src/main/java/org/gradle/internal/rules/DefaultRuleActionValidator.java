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

import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultRuleActionValidator implements RuleActionValidator {
    private static final String VALID_NO_TYPES = "Rule may not have an input parameter of type: %s.";
    private static final String VALID_MULTIPLE_TYPES = "Rule may not have an input parameter of type: %s. Second parameter must be of type: %s.";

    private final List<Class<?>> validInputType;

    public DefaultRuleActionValidator() {
        this.validInputType = Collections.emptyList();
    }

    public DefaultRuleActionValidator(Class<?>... validInputTypes) {
        this.validInputType = Arrays.asList(validInputTypes);
    }

    @Override
    public <T> RuleAction<? super T> validate(RuleAction<? super T> ruleAction) {
        validateInputTypes(ruleAction);
        return ruleAction;
    }

    private void validateInputTypes(RuleAction<?> ruleAction) {
        for (Class<?> inputType : ruleAction.getInputTypes()) {
            if (!validInputType.contains(inputType)) {
                throw new RuleActionValidationException(invalidParameterMessage(inputType));
            }
        }
    }

    private String invalidParameterMessage(Class<?> inputType) {
        if (validInputType.isEmpty()) {
            return String.format(VALID_NO_TYPES, inputType.getName());
        } else {
            return String.format(VALID_MULTIPLE_TYPES, inputType.getName(), validTypeNames());
        }
    }

    private String validTypeNames() {
        return validInputType.stream().map(ModelType::of).map(ModelType::toString).collect(Collectors.joining(" or "));
    }

}
