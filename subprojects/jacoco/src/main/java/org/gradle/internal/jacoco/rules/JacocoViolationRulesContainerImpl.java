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

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JacocoViolationRulesContainerImpl implements JacocoViolationRulesContainer {

    private final Instantiator instantiator;
    private boolean failOnViolation = true;
    private final List<JacocoViolationRule> rules = new ArrayList<JacocoViolationRule>();

    public JacocoViolationRulesContainerImpl(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void setFailOnViolation(boolean failOnViolation) {
        this.failOnViolation = failOnViolation;
    }

    @Override
    public boolean isFailOnViolation() {
        return failOnViolation;
    }

    @Override
    public List<JacocoViolationRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    @Override
    public JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction) {
        JacocoViolationRule validationRule = instantiator.newInstance(JacocoViolationRuleImpl.class);
        configureAction.execute(validationRule);
        rules.add(validationRule);
        return validationRule;
    }
}
