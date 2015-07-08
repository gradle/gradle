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
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.configure.ToolSettingNativeBinaryInitializer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.platform.base.test.TestSuiteContainer;

import java.io.File;

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
                        DefaultGoogleTestTestSuiteSpec googleTestSuite = (DefaultGoogleTestTestSuiteSpec) testSuite;
                        googleTestSuite.setTestedComponent(component);
                    }
                });
            }
        }

        @ComponentType
        public void registerGoogleTestSuiteSpecTest(ComponentTypeBuilder<GoogleTestTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteSpec.class);
        }

        @Finalize
        public void configureGoogleTestTestSuiteSources(TestSuiteContainer testSuites, @Path("buildDir") File buildDir) {
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
            final Action<NativeBinarySpec> setToolsAction = new ToolSettingNativeBinaryInitializer(languageTransforms);

            testSuites.withType(GoogleTestTestSuiteSpec.class).afterEach(new Action<GoogleTestTestSuiteSpec>() {
                @Override
                public void execute(final GoogleTestTestSuiteSpec testSuiteSpec) {
                    for (final NativeBinarySpec testedBinary : testSuiteSpec.getTestedComponent().getBinaries().withType(NativeBinarySpec.class).values()) {
                        if (testedBinary instanceof SharedLibraryBinary) {
                            // TODO:DAZ For now, we only create test suites for static library variants
                            continue;
                        }

                        final BinaryNamingScheme namingScheme = new DefaultBinaryNamingSchemeBuilder(((NativeBinarySpecInternal) testedBinary).getNamingScheme())
                            .withComponentName(testSuiteSpec.getBaseName())
                            .withTypeString("GoogleTestExe").build();
                        final NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);

                        testSuiteSpec.getBinaries().create(namingScheme.getLifecycleTaskName(), GoogleTestTestSuiteBinarySpec.class, new Action<GoogleTestTestSuiteBinarySpec>() {
                            @Override
                            public void execute(GoogleTestTestSuiteBinarySpec binary) {
                                DefaultGoogleTestTestSuiteBinary testBinary = (DefaultGoogleTestTestSuiteBinary) binary;
                                testBinary.setTestedBinary((NativeBinarySpecInternal) testedBinary);
                                testBinary.setNamingScheme(namingScheme);
                                testBinary.setResolver(resolver);
                                setToolsAction.execute(testBinary);
                                configure(testBinary, buildDir);
                            }
                        });
                    }
                }
            });
        }

        private void configure(DefaultGoogleTestTestSuiteBinary testBinary, File buildDir) {
            BinaryNamingScheme namingScheme = testBinary.getNamingScheme();
            PlatformToolProvider toolProvider = testBinary.getPlatformToolProvider();
            File binaryOutputDir = new File(new File(buildDir, "binaries"), namingScheme.getOutputDirectoryBase());
            String baseName = testBinary.getComponent().getBaseName();

            testBinary.setExecutableFile(new File(binaryOutputDir, toolProvider.getExecutableName(baseName)));
        }
    }
}
