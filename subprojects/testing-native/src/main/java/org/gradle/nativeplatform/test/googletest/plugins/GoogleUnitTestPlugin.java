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

package org.gradle.nativeplatform.test.googletest.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.cpp.plugins.CppUnitTestBasePlugin;
import org.gradle.nativeplatform.test.googletest.tasks.GoogleTest;
import org.gradle.testing.base.plugins.TestingBasePlugin;

public class GoogleUnitTestPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(TestingBasePlugin.class);
        project.getPluginManager().apply(CppUnitTestBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        // TODO: Replace with new native test task
        final GoogleTest testTask = tasks.create("runUnitTest", GoogleTest.class, new Action<GoogleTest>() {
            @Override
            public void execute(GoogleTest testTask) {
                testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                testTask.setDescription("Executes C++ unit tests.");

                // TODO: It would be nice if the installation was a thing we could get path/dependencies from vs going to the task
                final InstallExecutable installTask = (InstallExecutable) tasks.getByName("installUnitTestExecutable");
                testTask.getExecutable().set(installTask.getRunScript());
                testTask.dependsOn(installTask);
            }
        });

        tasks.getByName("check").dependsOn(testTask);
    }
}
