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

import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.internal.DefaultCppSourceSet;
import org.gradle.language.cpp.plugins.CppLangPlugin;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.model.*;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.test.TestSuiteContainer;

import java.io.File;
import java.util.Collections;

/**
 * A plugin that sets up the infrastructure for testing native binaries with GoogleTest.
 */
@Incubating
public class GoogleTestPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.apply(Collections.singletonMap("plugin", NativeBinariesTestPlugin.class));
        project.apply(Collections.singletonMap("plugin", CppLangPlugin.class));
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        // TODO:DAZ Test suites should belong to ComponentSpecContainer, and we could rely on more conventions from the base plugins
        @Defaults
        public void createGoogleTestTestSuitePerComponent(TestSuiteContainer testSuites, NamedDomainObjectSet<NativeComponentSpec> components, ProjectSourceSet projectSourceSet, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            for (NativeComponentSpec component : components) {
                testSuites.add(createGoogleTestTestSuite(component, instantiator, projectSourceSet, fileResolver));
            }
        }

        private GoogleTestTestSuiteSpec createGoogleTestTestSuite(final NativeComponentSpec testedComponent, Instantiator instantiator, ProjectSourceSet projectSourceSet, FileResolver fileResolver) {
            String suiteName = String.format("%sTest", testedComponent.getName());
            String path = testedComponent.getProjectPath();
            ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(path, suiteName);
            FunctionalSourceSet testSuiteSourceSet = createGoogleTestSources(instantiator, suiteName, projectSourceSet, fileResolver);
            GoogleTestTestSuiteSpec testSuiteSpec = BaseComponentSpec.create(DefaultGoogleTestTestSuiteSpec.class, id, testSuiteSourceSet, instantiator);
            testSuiteSpec.setTestedComponent(testedComponent);
            return testSuiteSpec;
        }

        private FunctionalSourceSet createGoogleTestSources(final Instantiator instantiator, final String suiteName, ProjectSourceSet projectSourceSet, final FileResolver fileResolver) {
            final FunctionalSourceSet functionalSourceSet = instantiator.newInstance(DefaultFunctionalSourceSet.class, suiteName, instantiator, projectSourceSet);
            functionalSourceSet.registerFactory(CppSourceSet.class, new NamedDomainObjectFactory<CppSourceSet>() {
                public CppSourceSet create(String name) {
                    return BaseLanguageSourceSet.create(DefaultCppSourceSet.class, name, suiteName, fileResolver, instantiator);
                }
            });
            return functionalSourceSet;
        }

        @Finalize
        public void configureGoogleTestTestSuiteSources(TestSuiteContainer testSuites, @Path("buildDir") File buildDir) {

            for (final GoogleTestTestSuiteSpec suite : testSuites.withType(GoogleTestTestSuiteSpec.class)) {
                FunctionalSourceSet suiteSourceSet = ((ComponentSpecInternal) suite).getSources();

                CppSourceSet testSources = suiteSourceSet.maybeCreate("cpp", CppSourceSet.class);
                testSources.getSource().srcDir(String.format("src/%s/%s", suite.getName(), "cpp"));
                testSources.getExportedHeaders().srcDir(String.format("src/%s/headers", suite.getName()));
            }
        }

        @Mutate
        public void createGoogleTestTestBinaries(final BinaryContainer binaries, TestSuiteContainer testSuites, @Path("buildDir") File buildDir, ServiceRegistry serviceRegistry, ITaskFactory taskFactory) {
            for (final GoogleTestTestSuiteSpec googleTestTestSuite : testSuites.withType(GoogleTestTestSuiteSpec.class)) {
                for (NativeBinarySpec testedBinary : googleTestTestSuite.getTestedComponent().getBinaries().withType(NativeBinarySpec.class)) {
                    if (testedBinary instanceof SharedLibraryBinary) {
                        // TODO:DAZ For now, we only create test suites for static library variants
                        continue;
                    }
                    DefaultGoogleTestTestSuiteBinary testBinary = createTestBinary(serviceRegistry, googleTestTestSuite, testedBinary, taskFactory);

                    configure(testBinary, buildDir);

                    googleTestTestSuite.getBinaries().add(testBinary);
                    binaries.add(testBinary);
                }
            }
        }

        private DefaultGoogleTestTestSuiteBinary createTestBinary(ServiceRegistry serviceRegistry, GoogleTestTestSuiteSpec googleTestTestSuite, NativeBinarySpec testedBinary, ITaskFactory taskFactory) {
            BinaryNamingScheme namingScheme = new DefaultBinaryNamingSchemeBuilder(((NativeBinarySpecInternal) testedBinary).getNamingScheme())
                    .withComponentName(googleTestTestSuite.getBaseName())
                    .withTypeString("GoogleTestExe").build();

            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            return DefaultGoogleTestTestSuiteBinary.create(googleTestTestSuite, (NativeBinarySpecInternal) testedBinary, namingScheme, resolver, instantiator, taskFactory);
        }

        private void configure(DefaultGoogleTestTestSuiteBinary testBinary, File buildDir) {
            BinaryNamingScheme namingScheme = testBinary.getNamingScheme();
            PlatformToolProvider toolProvider = testBinary.getPlatformToolProvider();
            File binaryOutputDir = new File(new File(buildDir, "binaries"), namingScheme.getOutputDirectoryBase());
            String baseName = testBinary.getComponent().getBaseName();

            testBinary.setExecutableFile(new File(binaryOutputDir, toolProvider.getExecutableName(baseName)));

            ((ExtensionAware) testBinary).getExtensions().create("cppCompiler", DefaultPreprocessingTool.class);

            // TODO:DAZ Not sure if this should be here...
            // Need "-pthread" when linking on Linux
            if (testBinary.getToolChain() instanceof GccCompatibleToolChain
                    && testBinary.getTargetPlatform().getOperatingSystem().isLinux()) {
                testBinary.getLinker().args("-pthread");
            }
        }
    }
}
