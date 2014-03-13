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
package org.gradle.nativebinaries.test.cunit.plugins
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.BinaryContainer
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.NativeProjectComponentIdentifier
import org.gradle.nativebinaries.internal.ProjectNativeComponentInternal
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.nativebinaries.test.TestSuiteContainer
import org.gradle.nativebinaries.test.cunit.CUnitTestSuite
import org.gradle.nativebinaries.test.cunit.internal.ConfigureCUnitTestSources
import org.gradle.nativebinaries.test.cunit.internal.CreateCUnitBinaries
import org.gradle.nativebinaries.test.cunit.internal.DefaultCUnitTestSuite
import org.gradle.nativebinaries.test.plugins.NativeBinariesTestPlugin

import javax.inject.Inject
/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator
    private final NativeDependencyResolver resolver;

    @Inject
    public CUnitPlugin(Instantiator instantiator, NativeDependencyResolver resolver) {
        this.instantiator = instantiator;
        this.resolver = resolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesTestPlugin.class)

        TestSuiteContainer testSuites = project.getExtensions().getByType(TestSuiteContainer)
        BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer)
        project.getExtensions().getByType(ExecutableContainer).all { Executable executable ->
            testSuites.add createCUnitTestSuite(executable, binaries, project)
        }
        project.getExtensions().getByType(LibraryContainer).all { Library library ->
            testSuites.add createCUnitTestSuite(library, binaries, project)
        }
    }

    private CUnitTestSuite createCUnitTestSuite(ProjectNativeComponent testedComponent, BinaryContainer binaries, ProjectInternal project) {
        String suiteName = "${testedComponent.name}Test"
        String path = (testedComponent as ProjectNativeComponentInternal).projectPath
        NativeProjectComponentIdentifier id = new NativeProjectComponentIdentifier(path, suiteName);
        CUnitTestSuite cUnitTestSuite = instantiator.newInstance(DefaultCUnitTestSuite, id, testedComponent);

        new ConfigureCUnitTestSources(project).apply(cUnitTestSuite)
        new CreateCUnitBinaries(project, instantiator, resolver).apply(cUnitTestSuite, binaries);
        return cUnitTestSuite;
    }
}