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
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.ComponentWithTargetMachines;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory;
import org.gradle.nativeplatform.test.TestSuiteComponent;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;
import java.util.Collections;
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
public class NativeTestingBasePlugin implements Plugin<Project> {
    private final TargetMachineFactory targetMachineFactory;

    private static final String TEST_TASK_NAME = "test";
    private static final String TEST_COMPONENT_NAME = "test";

    @Inject
    public NativeTestingBasePlugin(TargetMachineFactory targetMachineFactory) {
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(TestingBasePlugin.class);

        // Create test lifecycle task
        TaskContainer tasks = project.getTasks();

        final TaskProvider<Task> test = tasks.register(TEST_TASK_NAME, task -> task.dependsOn((Callable<Object>) () -> {
            TestSuiteComponent unitTestSuite = project.getComponents().withType(TestSuiteComponent.class).findByName(TEST_COMPONENT_NAME);
            if (unitTestSuite != null && unitTestSuite.getTestBinary().isPresent()) {
                return unitTestSuite.getTestBinary().get().getRunTask();
            }
            return null;
        }));

        project.getComponents().withType(TestSuiteComponent.class, testSuiteComponent -> {
            if (testSuiteComponent instanceof ComponentWithTargetMachines) {
                ComponentWithTargetMachines componentWithTargetMachines = (ComponentWithTargetMachines) testSuiteComponent;
                if (TEST_COMPONENT_NAME.equals(testSuiteComponent.getName())) {
                    test.configure(task -> task.dependsOn((Callable) () -> {
                        TargetMachine currentHost = ((DefaultTargetMachineFactory)targetMachineFactory).host();
                        boolean targetsCurrentMachine = componentWithTargetMachines.getTargetMachines().get().stream().anyMatch(targetMachine -> currentHost.getOperatingSystemFamily().equals(targetMachine.getOperatingSystemFamily()));
                        if (!targetsCurrentMachine) {
                            task.getLogger().warn("'" + testSuiteComponent.getName() + "' component in project '" + project.getPath() + "' does not target this operating system.");
                        }
                        return Collections.emptyList();
                    }));
                }
            }
        });

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(test));
    }
}
