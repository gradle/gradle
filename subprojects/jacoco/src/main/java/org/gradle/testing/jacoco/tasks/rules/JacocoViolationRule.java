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

import java.util.List;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * Jacoco violation rule.
 *
 * @since 3.3
 */
@Incubating
public interface JacocoViolationRule {

    void setEnabled(boolean enabled);

    /**
     * Indicates if the rule should be used when checking generated coverage metrics.
     */
    boolean isEnabled();

    void setScope(JacocoRuleScope scope);

    /**
     * Gets the scope for the rule defined by {@link JacocoRuleScope}. Defaults to BUNDLE.
     */
    JacocoRuleScope getScope();

    void setIncludes(List<String> includes);

    /**
     * List of elements that should be included in check. Names can use wildcards (* and ?).
     * If left empty, all elements will be included. Defaults to [*].
     */
    List<String> getIncludes();

    void setExcludes(List<String> excludes);

    /**
     * List of elements that should be excluded from check. Names can use wildcards (* and ?).
     * If left empty, no elements will be excluded. Defaults to an empty list.
     */
    List<String> getExcludes();

    /**
     * Gets all thresholds defined for this rule. Defaults to an empty list.
     */
    List<JacocoThreshold> getThresholds();

    /**
     * Adds a threshold for this rule. There's no limitation to the number of thresholds that can be added.
     */
    JacocoThreshold threshold(@DelegatesTo(value = JacocoThreshold.class, strategy = DELEGATE_FIRST) Closure configureClosure);

    JacocoThreshold threshold(Action<? super JacocoThreshold> configureAction);
}
