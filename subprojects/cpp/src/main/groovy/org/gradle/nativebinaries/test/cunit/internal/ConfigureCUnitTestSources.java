/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.test.cunit.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.nativebinaries.test.cunit.CUnitTestSuite;
import org.gradle.nativebinaries.test.cunit.tasks.GenerateCUnitLauncher;

import java.io.File;

public class ConfigureCUnitTestSources {
    private final ProjectInternal project;

    public ConfigureCUnitTestSources(ProjectInternal project) {
        this.project = project;
    }

    public void apply(CUnitTestSuite cUnitTestSuite) {
        FunctionalSourceSet suiteSourceSet = createSuiteSources(cUnitTestSuite);

        CSourceSet launcherSources = suiteSourceSet.maybeCreate("cunitLauncher", CSourceSet.class);
        cUnitTestSuite.source(launcherSources);
        createCUnitLauncherTask(cUnitTestSuite, launcherSources);

        CSourceSet testSources = suiteSourceSet.maybeCreate("cunit", CSourceSet.class);
        cUnitTestSuite.source(testSources);
        testSources.lib(launcherSources);

        ProjectNativeComponent testedComponent = cUnitTestSuite.getTestedComponent();
        cUnitTestSuite.source(testedComponent.getSource());

        testSources.lib(testedComponent.getSource().withType(CSourceSet.class));
    }

    private FunctionalSourceSet createSuiteSources(CUnitTestSuite cUnitTestSuite) {
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        return projectSourceSet.maybeCreate(cUnitTestSuite.getName());
    }

    private void createCUnitLauncherTask(CUnitTestSuite suite, CSourceSet cunitSourceSet) {
        String taskName = suite.getName() + "CUnitLauncher";
        GenerateCUnitLauncher skeletonTask = project.getTasks().create(taskName, GenerateCUnitLauncher.class);

        File baseDir = new File(project.getBuildDir(), "src/" + taskName);
        skeletonTask.setSourceDir(new File(baseDir, "cunit"));
        skeletonTask.setHeaderDir(new File(baseDir, "headers"));
        cunitSourceSet.generatedBy(skeletonTask);
    }
}
