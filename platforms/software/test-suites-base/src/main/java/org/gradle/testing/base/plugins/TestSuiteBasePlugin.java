/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.internal.DefaultTestingExtension;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.wrapper.Wrapper;

/**
 * Base test suite functionality. Makes an extension named "testing" available to the project.
 *
 * @since 7.3
 */
@Incubating
public abstract class TestSuiteBasePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create(TestingExtension.class, "testing", DefaultTestingExtension.class);
        if (project.getParent() == null) {
            project.getTasks().register("wrapper", Wrapper.class, wrapper -> {
                wrapper.setGroup("Build Setup");
                wrapper.setDescription("Generates Gradle wrapper files.");
                wrapper.getNetworkTimeout().convention(10000);

                Task initTask = project.getTasks().findByName("init");
                wrapper.onlyIf("The init task did not fail if it was executed", new WrapperOnlyIfSpec(initTask));
            });
        }
    }

    private static class WrapperOnlyIfSpec implements Spec<Task> {

        private final Task initTask;

        private WrapperOnlyIfSpec(Task initTask) {
            this.initTask = initTask;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            if (initTask != null && initTask.getState().getExecuted()) {
                return initTask.getState().getFailure() == null;
            }
            return true;
        }
    }

    private static class WrapperOnlyIfSpec implements Spec<Task> {

        private final Task initTask;

        private WrapperOnlyIfSpec(Task initTask) {
            this.initTask = initTask;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            if (initTask != null && initTask.getState().getExecuted()) {
                return initTask.getState().getFailure() == null;
            }
            return true;
        }
    }
}
