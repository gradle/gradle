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

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.NativeProjectComponentIdentifier;
import org.gradle.nativebinaries.language.internal.DefaultPreprocessingTool;
import org.gradle.nativebinaries.test.TestSuiteContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CreateCUnitTestSuites implements Action<TestSuiteContainer> {
    private final ProjectInternal project;

    public CreateCUnitTestSuites(ProjectInternal project) {
        this.project = project;
    }

    public void execute(TestSuiteContainer testSuites) {
        for (ProjectNativeComponent component : allComponents()) {
            String suiteName = component.getName() + "Test";
            NativeProjectComponentIdentifier id = new NativeProjectComponentIdentifier(project.getPath(), suiteName);
            DefaultCUnitTestSuite cUnitTestSuite = new DefaultCUnitTestSuite(id, component);
            addCLanguageExtension(cUnitTestSuite);
            testSuites.add(cUnitTestSuite);
        }
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

    // TODO:DAZ This is getting terrible. Add a components container, then this would be handled by the CNativeBinariesPlugin
    private void addCLanguageExtension(ProjectNativeComponent component) {
        component.getBinaries().all(new Action<NativeBinary>() {
            public void execute(NativeBinary nativeBinary) {
                ((ExtensionAware) nativeBinary).getExtensions().create("cCompiler", DefaultPreprocessingTool.class);
            }
        });
    }

}
