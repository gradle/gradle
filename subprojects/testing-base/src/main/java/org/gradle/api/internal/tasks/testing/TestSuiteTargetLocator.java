/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestSuiteTarget;
import org.gradle.testing.base.TestingExtension;

import javax.annotation.Nullable;
import java.util.SortedMap;

/**
 * This class contains methods for locating test suite related objects within a project.
 */
public final class TestSuiteTargetLocator {
    private final Project project;

    public TestSuiteTargetLocator(Project project) {
        this.project = project;
    }

    /**
     * Given a {@link AbstractTestTask}, returns the {@link TestSuiteTarget} that corresponds to the task,
     * if one exists in the associated project.
     * <p />
     * Note that this will eagerly realize all the suites and targets in the project, if they haven't already been realized.
     *
     * @param test the test task to investigate
     * @return the target which created the given task, or {@code null} if none is present
     */
    @Nullable
    public TestSuiteTarget getTargetForTest(AbstractTestTask test) {
        TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
        if (testing != null) {
            SortedMap<String, TestSuite> suites = testing.getSuites().getAsMap();
            for (TestSuite suite : suites.values()) {
                SortedMap<String, ? extends TestSuiteTarget> targets = suite.getTargets().getAsMap();
                for (TestSuiteTarget target : targets.values()) {
                    if (ObjectUtils.equals(target.getTestTask().getName(), test.getName())) {
                        return target;
                    }
                }
            }
        }

        return null;
    }
}
