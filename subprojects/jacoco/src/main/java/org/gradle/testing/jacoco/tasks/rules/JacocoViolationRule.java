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
 * Defines a Jacoco violation rule.
 *
 * @since 4.0
 */
@Incubating
public interface JacocoViolationRule {

    void setEnabled(boolean enabled);

    /**
     * Indicates if the rule should be used when checking generated coverage metrics.
     */
    boolean isEnabled();

    void setElement(String element);

    /**
     * Gets the element for the rule as defined by
     * <a href="http://www.eclemma.org/jacoco/trunk/doc/api/org/jacoco/core/analysis/ICoverageNode.ElementType.html">org.jacoco.core.analysis.ICoverageNode.ElementType</a>.
     * Valid scope values are BUNDLE, PACKAGE, CLASS, SOURCEFILE and METHOD. Defaults to BUNDLE.
     */
    String getElement();

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
     * Gets all limits defined for this rule. Defaults to an empty list.
     */
    List<JacocoLimit> getLimits();

    /**
     * Adds a limit for this rule. Any number of limits can be added.
     */
    JacocoLimit limit(@DelegatesTo(value = JacocoLimit.class, strategy = DELEGATE_FIRST) Closure configureClosure);

    JacocoLimit limit(Action<? super JacocoLimit> configureAction);
}
