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

package org.gradle.testing.jacoco.tasks.rules;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;

import java.util.List;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * The violation rules configuration for the {@link org.gradle.testing.jacoco.tasks.JacocoReport} task.
 *
 * @since 4.0
 */
@Incubating
public interface JacocoViolationRulesContainer {

    void setFailOnViolation(boolean ignore);

    /**
     * Specifies whether build should fail in case of rule violations. Defaults to true.
     */
    @Internal
    boolean isFailOnViolation();

    /**
     * Gets all violation rules. Defaults to an empty list.
     */
    @Internal
    List<JacocoViolationRule> getRules();

    /**
     * Adds a violation rule. There's no limitation to the number of rules that can be added.
     */
    JacocoViolationRule rule(@DelegatesTo(value = JacocoViolationRule.class, strategy = DELEGATE_FIRST) Closure configureClosure);

    JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction);
}
