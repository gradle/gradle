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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jacoco.AntJacocoCheck;
import org.gradle.internal.jacoco.JacocoCheckResult;
import org.gradle.internal.jacoco.rules.JacocoViolationRulesContainerImpl;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import java.io.File;

/**
 * Task for verifying code coverage metrics. Fails the task if violations are detected based on specified rules.
 * <p>
 * Requires JaCoCo version &gt;= 0.6.3.
 *
 * @since 3.4
 */
@Incubating
public class JacocoCoverageVerification extends JacocoReportBase {

    private final JacocoViolationRulesContainer violationRules;

    public JacocoCoverageVerification() {
        super();
        Instantiator instantiator = getInstantiator();
        violationRules = instantiator.newInstance(JacocoViolationRulesContainerImpl.class, instantiator);
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
    public JacocoViolationRulesContainer violationRules(Action<? super JacocoViolationRulesContainer> configureAction) {
        configureAction.execute(violationRules);
        return violationRules;
    }

    @TaskAction
    public void check() {
        final Spec<File> fileExistsSpec = new Spec<File>() {
            @Override
            public boolean isSatisfiedBy(File file) {
                return file.exists();
            }
        };

        JacocoCheckResult checkResult = new AntJacocoCheck(getAntBuilder()).execute(
                getJacocoClasspath(),
                getProject().getName(),
                getAllClassDirs().filter(fileExistsSpec),
                getAllSourceDirs().filter(fileExistsSpec),
                getExecutionData(),
                getViolationRules()
        );

        if (!checkResult.isSuccess()) {
            throw new GradleException(checkResult.getFailureMessage());
        }
    }
}
