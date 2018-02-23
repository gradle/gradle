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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.util.List;

/**
 * Defines a Jacoco violation rule.
 *
 * @since 3.4
 */
@Incubating
public interface JacocoViolationRule extends Serializable {

    void setEnabled(boolean enabled);

    /**
     * Indicates if the rule should be used when checking generated coverage metrics. Defaults to true.
     */
    @Input
    boolean isEnabled();

    /**
     * Sets element for the rule.
     *
     * @param element Element
     */
    void setElement(String element);

    /**
     * Gets the element for the rule as defined by
     * <a href="http://www.eclemma.org/jacoco/trunk/doc/api/org/jacoco/core/analysis/ICoverageNode.ElementType.html">org.jacoco.core.analysis.ICoverageNode.ElementType</a>.
     * Valid scope values are BUNDLE, PACKAGE, CLASS, SOURCEFILE and METHOD. Defaults to BUNDLE.
     */
    @Input
    String getElement();

    /**
     * Sets list of elements that should be included in check.
     *
     * @param includes Inclusions
     */
    void setIncludes(List<String> includes);

    /**
     * List of elements that should be included in check. Names can use wildcards (* and ?).
     * If left empty, all elements will be included. Defaults to [*].
     */
    @Input
    List<String> getIncludes();

    /**
     * Sets list of elements that should be excluded from check.
     *
     * @param excludes Exclusions
     */
    void setExcludes(List<String> excludes);

    /**
     * List of elements that should be excluded from check. Names can use wildcards (* and ?).
     * If left empty, no elements will be excluded. Defaults to an empty list.
     */
    @Input
    List<String> getExcludes();

    /**
     * Gets all limits defined for this rule. Defaults to an empty list.
     */
    @Input
    List<JacocoLimit> getLimits();

    /**
     * Adds a limit for this rule. Any number of limits can be added.
     */
    JacocoLimit limit(Action<? super JacocoLimit> configureAction);
}
