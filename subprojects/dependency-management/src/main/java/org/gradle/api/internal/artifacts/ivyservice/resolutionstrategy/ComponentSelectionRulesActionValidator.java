/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.rules.DefaultRuleActionValidator;
import org.gradle.internal.rules.RuleAction;
import org.gradle.util.DeprecationLogger;

import java.util.List;

class ComponentSelectionRulesActionValidator extends DefaultRuleActionValidator {
    private final String methodPrefix;

    ComponentSelectionRulesActionValidator(List<Class<?>> validInputTypes, String methodPrefix) {
        super(validInputTypes);
        this.methodPrefix = methodPrefix;
    }

    @Override
    public <T> RuleAction<? super T> validate(RuleAction<? super T> ruleAction) {
        RuleAction<? super T> result = super.validate(ruleAction);
        if (!ruleAction.getInputTypes().isEmpty()) {
            DeprecationLogger.nagUserOfDeprecated("The method ComponentSelectionRules." + methodPrefix + "Closure) with injection of ComponentMetadata and/or IvyModuleDescriptor", "Use the new methods on ComponentSelection instead.");
        }
        return result;
    }
}
