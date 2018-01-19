/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.nativeplatform.test.TestSuiteComponent;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import java.util.concurrent.Callable;

/**
 * Common base plugin for all native testing plugins.
 *
 * <p>Expects plugins to register the native test suites in the {@link Project#getComponents()} container, and defines a number of rules that act on these components to configure them.</p>
 *
 * <ul>
 *
 * <li>Adds a {@code "test"} task.</li>
 *
 * <li>Configures the {@code "test"} task to run the tests of the {@code test} component, if present. Expects the test component to be of type {@link TestSuiteComponent}.</li>
 *
 * </ul>
 * @since 4.5
 */
@Incubating
public class NativeTestingBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(TestingBasePlugin.class);

        // Create test lifecycle task
        TaskContainer tasks = project.getTasks();
        Task test = tasks.create("test");
        test.dependsOn(new Callable<Object>() {
            @Override
            public Object call() {
                TestSuiteComponent unitTestSuite = project.getComponents().withType(TestSuiteComponent.class).findByName("test");
                if (unitTestSuite == null) {
                    return null;
                }
                return unitTestSuite.getTestBinary().get().getRunTask().get();
            }
        });

        tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(test);
    }
}
