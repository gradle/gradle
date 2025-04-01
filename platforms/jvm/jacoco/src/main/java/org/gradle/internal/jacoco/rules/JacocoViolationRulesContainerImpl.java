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
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import javax.inject.Inject;
import java.util.List;

public abstract class JacocoViolationRulesContainerImpl implements JacocoViolationRulesContainer {

    private final ObjectFactory objectFactory;
    private final ListProperty<JacocoViolationRule> rules;

    @Inject
    public JacocoViolationRulesContainerImpl(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        this.rules = objectFactory.listProperty(JacocoViolationRule.class);
        getFailOnViolation().convention(true);
    }

    @Override
    public abstract Property<Boolean> getFailOnViolation();

    @Override
    public Provider<List<JacocoViolationRule>> getRules() {
        // Make it read-only
        return rules.map(SerializableLambdas.transformer(__ -> __));
    }

    @Override
    public JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction) {
        JacocoViolationRule validationRule = objectFactory.newInstance(JacocoViolationRuleImpl.class);
        configureAction.execute(validationRule);
        rules.add(validationRule);
        return validationRule;
    }
}
