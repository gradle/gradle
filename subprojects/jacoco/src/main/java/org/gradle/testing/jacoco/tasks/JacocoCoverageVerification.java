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

package org.gradle.testing.jacoco.tasks;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jacoco.AntJacocoCheck;
import org.gradle.internal.jacoco.rules.JacocoViolationRulesContainerImpl;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import static groovy.lang.Closure.DELEGATE_FIRST;

/**
 * Task for verifying code coverage metrics. Fails the task if violations are detected based on specified rules.
 * <p>
 * Requires JaCoCo version >= 0.6.3.
 *
 * @since 4.0
 */
@Incubating
public class JacocoCoverageVerification extends JacocoReportBase {

    private final JacocoViolationRulesContainer violationRules;

    public JacocoCoverageVerification() {
        super();
        violationRules = getInstantiator().newInstance(JacocoViolationRulesContainerImpl.class);
    }

    /**
     * Returns the violation rules set for this task.
     *
     * @return Violation rules container
     */
    @Nested
    public JacocoViolationRulesContainer getViolationRules() {
        return violationRules;
    }

    /**
     * Configures the violation rules for this task.
     */
    @Incubating
    public JacocoViolationRulesContainer violationRules(@DelegatesTo(value = JacocoViolationRulesContainer.class, strategy = DELEGATE_FIRST) Closure closure) {
        return violationRules(new ClosureBackedAction<JacocoViolationRulesContainer>(closure));
    }

    /**
     * Configures the violation rules for this task.
     */
    @Incubating
    public JacocoViolationRulesContainer violationRules(Action<? super JacocoViolationRulesContainer> configureAction) {
        configureAction.execute(violationRules);
        return violationRules;
    }

    @TaskAction
    public void check() {
        new AntJacocoCheck(getAntBuilder()).execute(
                getJacocoClasspath(),
                getProject().getName(),
                getAllClassDirs().filter(fileExistsSpec),
                getAllSourceDirs().filter(fileExistsSpec),
                getExecutionData(),
                getViolationRules()
        );
    }
}
