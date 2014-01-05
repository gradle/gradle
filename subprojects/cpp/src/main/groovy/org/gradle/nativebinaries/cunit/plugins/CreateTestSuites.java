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
package org.gradle.nativebinaries.cunit.plugins;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.cunit.TestSuite;
import org.gradle.nativebinaries.cunit.TestSuiteContainer;
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class CreateTestSuites extends ModelRule {
    private final ProjectInternal project;

    public CreateTestSuites(ProjectInternal project) {
        this.project = project;
    }

    // TODO:DAZ Will we combine multiple test frameworks into a single 'suite'? (eg cppUnit + cUnit)
    public void create(TestSuiteContainer testSuites) {
        for (ProjectNativeComponent component : allComponents()) {
            String suiteName = component.getName() + "Test";
            TestSuite testSuite = testSuites.create(suiteName);
            addCLanguageExtension(testSuite);
            addComponentCUnitSources(testSuite, component);
        }
    }

    // TODO:DAZ This is getting terrible. Add a components container, then this would be handled by the CNativeBinariesPlugin
    private void addCLanguageExtension(ProjectNativeComponent component) {
        component.getBinaries().all(new Action<NativeBinary>() {
            public void execute(NativeBinary nativeBinary) {
                ((ExtensionAware) nativeBinary).getExtensions().create("cCompiler", DefaultPreprocessingTool.class);
            }
        });
    }

    // TODO:DAZ This should be in a separate rule
    private void addComponentCUnitSources(TestSuite suite, ProjectNativeComponent testedComponent) {
        ProjectSourceSet projectSourceSet = project.getExtensions().getByType(ProjectSourceSet.class);
        FunctionalSourceSet componentTestSources = projectSourceSet.maybeCreate(suite.getName());
        // TODO:DAZ Add PolyMorphicDomainObjectContainer.maybeCreate(name, type)
        CSourceSet cunitSourceSet = (CSourceSet) componentTestSources.findByName("cunit");
        if (cunitSourceSet == null) {
            cunitSourceSet = componentTestSources.create("cunit", CSourceSet.class);
        }
        // TODO:DAZ This duplicates ApplySourceSetConventions, but is executed _after_ that rule.
        if (cunitSourceSet.getSource().getSrcDirs().isEmpty()) {
            cunitSourceSet.getSource().srcDir(String.format("src/%s/%s", componentTestSources.getName(), cunitSourceSet.getName()));
        }
        // TODO:DAZ Allow sourceSet.lib Collection<LanguageSourceSet>
        for (LanguageSourceSet componentSourceSet : testedComponent.getSource().withType(CSourceSet.class)) {
            cunitSourceSet.lib(componentSourceSet);
        }
        suite.source(cunitSourceSet);
        suite.source(testedComponent.getSource());
    }

    private Collection<ProjectNativeComponent> allComponents() {
        ExecutableContainer executables = project.getExtensions().getByType(ExecutableContainer.class);
        LibraryContainer libraries = project.getExtensions().getByType(LibraryContainer.class);

        List<ProjectNativeComponent> components = new ArrayList<ProjectNativeComponent>();
        for (Library library : libraries) {
            components.add(library);
        }
        for (Executable executable : executables) {
            components.add(executable);
        }
        return components;
    }

}
