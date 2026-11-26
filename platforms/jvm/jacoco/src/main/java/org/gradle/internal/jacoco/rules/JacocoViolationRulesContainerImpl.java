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
import org.gradle.api.internal.provider.views.ListPropertyListView;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainerInputs;

import java.util.Collections;
import java.util.List;

public class JacocoViolationRulesContainerImpl implements JacocoViolationRulesContainer {

    private final Instantiator instantiator;
    private final JacocoViolationRulesContainerInputs lazyProperties;

    public JacocoViolationRulesContainerImpl(Instantiator instantiator, JacocoViolationRulesContainerInputs lazyProperties) {
        this.instantiator = instantiator;
        this.lazyProperties = lazyProperties;
        lazyProperties.getFailOnViolation().convention(true);
    }

    @Internal
    public JacocoViolationRulesContainerInputs getLazyProperties() {
        return lazyProperties;
    }

    @Override
    public void setFailOnViolation(boolean failOnViolation) {
        lazyProperties.getFailOnViolation().set(failOnViolation);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.failOnViolation")
    public boolean isFailOnViolation() {
        return lazyProperties.getFailOnViolation().get();
    }

    @Override
    @BackedByLazyProperty("lazyProperties.rules")
    public List<JacocoViolationRule> getRules() {
        return Collections.unmodifiableList(new ListPropertyListView<>(lazyProperties.getRules()));
    }

    @Override
    public JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction) {
        JacocoViolationRule validationRule = instantiator.newInstance(JacocoViolationRuleImpl.class);
        configureAction.execute(validationRule);
        lazyProperties.getRules().add(validationRule);
        return validationRule;
    }
}
