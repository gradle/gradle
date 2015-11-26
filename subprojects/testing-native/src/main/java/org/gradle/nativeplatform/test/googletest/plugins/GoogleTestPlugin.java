/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.plugins.CppLangPlugin;
import org.gradle.model.*;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.test.TestSuiteContainer;

import java.io.File;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.createNativeTestSuiteBinaries;

/**
 * A plugin that sets up the infrastructure for testing native binaries with GoogleTest.
 */
@Incubating
public class GoogleTestPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CppLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Defaults
        public void createGoogleTestTestSuitePerComponent(TestSuiteContainer testSuites, ModelMap<NativeComponentSpec> components) {
            for (final NativeComponentSpec component : components.values()) {
                final String suiteName = String.format("%sTest", component.getName());
                testSuites.create(suiteName, GoogleTestTestSuiteSpec.class, new Action<GoogleTestTestSuiteSpec>() {
                    @Override
                    public void execute(GoogleTestTestSuiteSpec testSuite) {
                        testSuite.setTestedComponent(component);
                    }
                });
            }
        }

        @ComponentType
        public void registerGoogleTestSuiteSpecTest(ComponentTypeBuilder<GoogleTestTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteSpec.class);
        }

        @Finalize
        public void configureGoogleTestTestSuiteSources(TestSuiteContainer testSuites) {
            for (final GoogleTestTestSuiteSpec suite : testSuites.withType(GoogleTestTestSuiteSpec.class).values()) {
                if (!suite.getSources().containsKey("cpp")) {
                    suite.getSources().create("cpp", CppSourceSet.class);
                }
            }
        }

        @BinaryType
        public void registerGoogleTestSuiteBinaryType(BinaryTypeBuilder<GoogleTestTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteBinary.class);
        }

        @Mutate
        public void createGoogleTestTestBinaries(TestSuiteContainer testSuites, @Path("buildDir") final File buildDir,
                                                 LanguageTransformContainer languageTransforms, final ServiceRegistry serviceRegistry, final ITaskFactory taskFactory) {
            createNativeTestSuiteBinaries(testSuites, GoogleTestTestSuiteSpec.class, GoogleTestTestSuiteBinarySpec.class, "GoogleTestExe", buildDir, serviceRegistry);
       }
   }

}
