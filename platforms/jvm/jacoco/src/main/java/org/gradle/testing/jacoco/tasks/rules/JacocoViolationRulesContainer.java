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
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.util.List;

/**
 * The violation rules configuration for the {@link org.gradle.testing.jacoco.tasks.JacocoReport} task.
 *
 * @since 3.4
 */
public interface JacocoViolationRulesContainer {

    /**
     * Specifies whether build should fail in case of rule violations. Defaults to true.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    Property<Boolean> getFailOnViolation();

    @Internal
    @Deprecated
    default Property<Boolean> getIsFailOnViolation() {
        ProviderApiDeprecationLogger.logDeprecation(JacocoViolationRulesContainer.class, "getIsFailOnViolation()", "getFailOnViolation()");
        return getFailOnViolation();
    }

    /**
     * Gets all violation rules. Defaults to an empty list.
     */
    @Input
    @ReplacesEagerProperty
    Provider<List<JacocoViolationRule>> getRules();

    /**
     * Adds a violation rule. Any number of rules can be added.
     */
    JacocoViolationRule rule(Action<? super JacocoViolationRule> configureAction);
}
