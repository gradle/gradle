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

import org.gradle.api.Transformer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultRuleActionValidator<T> implements RuleActionValidator<T> {
    private static final String VALID_SINGLE_TYPES = "Rule may not have an input parameter of type: %s. Second parameter must be of type: %s.";
    private static final String VALID_MULTIPLE_TYPES = "Rule may not have an input parameter of type: %s. Valid types (for the second and subsequent parameters) are: %s.";

    private final List<Class<?>> validInputTypes;

    public DefaultRuleActionValidator(List<Class<?>> validInputTypes) {
        this.validInputTypes = validInputTypes;
    }

    public RuleAction<? super T> validate(RuleAction<? super T> ruleAction) {
        validateInputTypes(ruleAction);
        return ruleAction;
    }

    private void validateInputTypes(RuleAction<? super T> ruleAction) {
        for (Class<?> inputType : ruleAction.getInputTypes()) {
            if (!validInputTypes.contains(inputType)) {
                throw new RuleActionValidationException(invalidParameterMessage(inputType));
            }
        }
    }

    private String invalidParameterMessage(Class<?> inputType) {
        if (validInputTypes.size() == 1) {
            return String.format(VALID_SINGLE_TYPES, inputType.getName(), className(validInputTypes.get(0)));
        }
        return String.format(VALID_MULTIPLE_TYPES, inputType.getName(),
                             CollectionUtils.collect(validInputTypes, new ClassNameTransformer()));
    }

    private static String className(Class<?> aClass) {
        return ModelType.of(aClass).toString();
    }

    private static class ClassNameTransformer implements Transformer<String, Class<?>> {
        public String transform(Class<?> aClass) {
            return className(aClass);
        }
    }
}
