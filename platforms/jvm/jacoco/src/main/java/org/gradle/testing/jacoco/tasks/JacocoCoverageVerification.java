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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jacoco.JacocoCoverageAction;
import org.gradle.internal.jacoco.rules.JacocoViolationRuleImpl.SerializableJacocoViolationRule;
import org.gradle.internal.jacoco.rules.JacocoViolationRulesContainerImpl;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Task for verifying code coverage metrics. Fails the task if violations are detected based on specified rules.
 * <p>
 * Requires JaCoCo version &gt;= 0.6.3.
 *
 * @since 3.4
 */
@CacheableTask
public abstract class JacocoCoverageVerification extends JacocoReportBase {

    private final JacocoViolationRulesContainer violationRules;

    public JacocoCoverageVerification() {
        super();
        ObjectFactory objectFactory = getProject().getObjects();
        violationRules = objectFactory.newInstance(JacocoViolationRulesContainerImpl.class);
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
     * For internal use only. This property exists, because only tasks with outputs can be up-to-date and cached.
     */
    @OutputFile
    protected File getDummyOutputFile() {
        return new File(getTemporaryDir(), "success.txt");
    }

    private final String projectName = getProject().getName();

    /**
     * Configures the violation rules for this task.
     */
    public JacocoViolationRulesContainer violationRules(Action<? super JacocoViolationRulesContainer> configureAction) {
        configureAction.execute(violationRules);
        return violationRules;
    }

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void check() throws IOException {
        WorkQueue queue = getWorkerExecutor().classLoaderIsolation();
        queue.submit(JacocoCoverageAction.class, parameters -> {
            parameters.getAntLibraryClasspath().convention(getJacocoClasspath());
            parameters.getProjectName().convention(projectName);
            parameters.getEncoding().convention(getSourceEncoding());
            parameters.getAllSourcesDirs().convention(getAllSourceDirs());
            parameters.getAllClassesDirs().convention(getAllClassDirs());
            parameters.getExecutionData().convention(getExecutionData());

            parameters.getFailOnViolation().convention(getViolationRules().getFailOnViolation());
            parameters.getRules().convention(
                getViolationRules().getRules().get().stream()
                    .map(SerializableJacocoViolationRule::of)
                    .collect(Collectors.toSet())
            );

            parameters.getDummyOutputFile().fileValue(getDummyOutputFile());
        });
    }
}
