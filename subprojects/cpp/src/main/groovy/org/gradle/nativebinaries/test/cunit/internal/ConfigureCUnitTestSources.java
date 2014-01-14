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
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.nativebinaries.test.ProjectComponentTestSuite;
import org.gradle.nativebinaries.test.TestSuiteContainer;
import org.gradle.nativebinaries.test.cunit.CUnitTestSuite;
import org.gradle.nativebinaries.test.cunit.tasks.GenerateCUnitLauncher;

import java.io.File;

public class ConfigureCUnitTestSources extends ModelRule {
    private final ProjectInternal project;

    public ConfigureCUnitTestSources(ProjectInternal project) {
        this.project = project;
    }

    public void configureCUnitSources(TestSuiteContainer testSuites) {
        for (CUnitTestSuite cUnitTestSuite : testSuites.withType(CUnitTestSuite.class)) {
            createCUnitSources(cUnitTestSuite);
        }
    }

    private void createCUnitSources(CUnitTestSuite cUnitTestSuite) {
        CSourceSet cunitSourceSet = createCUnitSourceSet(cUnitTestSuite);
        cUnitTestSuite.source(cunitSourceSet);

        // TODO:DAZ This duplicates ApplySourceSetConventions, but is executed _after_ that rule.
        if (cunitSourceSet.getSource().getSrcDirs().isEmpty()) {
            cunitSourceSet.getSource().srcDir(String.format("src/%s/%s", cUnitTestSuite.getName(), cunitSourceSet.getName()));
        }

        if (cUnitTestSuite instanceof ProjectComponentTestSuite) {
            ProjectNativeComponent testedComponent = ((ProjectComponentTestSuite) cUnitTestSuite).getTestedComponent();
            cunitSourceSet.lib(testedComponent.getSource().withType(CSourceSet.class));
            cUnitTestSuite.source(testedComponent.getSource());
        }

        createCUnitLauncherTask(cunitSourceSet);
    }

    private CSourceSet createCUnitSourceSet(CUnitTestSuite cUnitTestSuite) {
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        FunctionalSourceSet componentTestSources = projectSourceSet.maybeCreate(cUnitTestSuite.getName());
        return componentTestSources.maybeCreate("cunit", CSourceSet.class);
    }

    private void createCUnitLauncherTask(CSourceSet cunitSourceSet) {
        String taskName = cunitSourceSet.getName() + "Launcher";
        GenerateCUnitLauncher skeletonTask = project.getTasks().create(taskName, GenerateCUnitLauncher.class);

        // TODO:DAZ Can't use 'generatedBy' because the ConfigureGeneratedSourceSets action runs before this (need to make it a rule)
        skeletonTask.setSourceDir(new File(project.getBuildDir(), "src/" + taskName));
        cunitSourceSet.builtBy(skeletonTask);
        cunitSourceSet.getSource().srcDir(skeletonTask.getSourceDir());
    }
}
