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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;

import java.util.List;

/**
 * The validation rules configuration for the {@link org.gradle.testing.jacoco.tasks.JacocoReport} task.
 *
 * @since 3.3
 */
@Incubating
public interface JacocoValidationRulesContainer {

    void setIgnoreFailures(boolean ignore);

    /**
     * Specifies whether build should fail in case of rule violations.
     */
    @Internal
    boolean isIgnoreFailures();

    /**
     * Gets all validation rules. Defaults to an empty list.
     */
    @Internal
    List<JacocoValidationRule> getRules();

    /**
     * Adds a validation rule. There's no limitation to the number of rules that can be added.
     */
    JacocoValidationRule rule(Closure configureClosure);

    JacocoValidationRule rule(Action<? super JacocoValidationRule> configureAction);
}
