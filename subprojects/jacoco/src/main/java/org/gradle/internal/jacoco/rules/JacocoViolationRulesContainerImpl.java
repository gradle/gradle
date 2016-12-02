/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco.rules;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JacocoViolationRulesContainerImpl implements JacocoViolationRulesContainer {

    private boolean failOnViolation;
    private final List<JacocoViolationRule> rules = new ArrayList<JacocoViolationRule>();

    public void setFailOnViolation(boolean failOnViolation) {
        this.failOnViolation = failOnViolation;
    }

    public boolean isFailOnViolation() {
        return failOnViolation;
    }

    @Override
    public List<JacocoViolationRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    @Override
    public JacocoViolationRule rule(Closure configureClosure) {
        return rule(ClosureBackedAction.of(configureClosure));
    }

    @Override
    public JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction) {
        JacocoViolationRule validationRule = new JacocoViolationRuleImpl();
        configureAction.execute(validationRule);
        rules.add(validationRule);
        return validationRule;
    }
}
