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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry;
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
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentSpecIdentifier;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier;
import org.gradle.platform.base.test.TestSuiteContainer;
import org.gradle.platform.base.test.TestSuiteSpec;

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

        // TODO:DAZ Test suites should belong to ComponentSpecContainer, and we could rely on more conventions from the base plugins
        @Defaults
        public void createGoogleTestTestSuitePerComponent(TestSuiteContainer testSuites, ModelMap<NativeComponentSpec> components, ProjectSourceSet projectSourceSet, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            FileResolver fileResolver = serviceRegistry.get(FileResolver.class);

            for (final NativeComponentSpec component : components.values()) {
                String suiteName = String.format("%sTest", component.getName());
                testSuites.create(suiteName, GoogleTestTestSuiteSpec.class, new Action<GoogleTestTestSuiteSpec>() {
                    @Override
                    public void execute(GoogleTestTestSuiteSpec googleTestTestSuiteSpec) {
                        googleTestTestSuiteSpec.setTestedComponent(component);
                    }
                });
            }
        }

        @Mutate
        public void registerGoogleTestSuiteSpecFactory(RuleAwareNamedDomainObjectFactoryRegistry<TestSuiteSpec> factoryRegistry,
                                                       final ProjectSourceSet projectSourceSet,
                                                       final ProjectIdentifier projectIdentifier,
                                                       ServiceRegistry serviceRegistry) {
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            factoryRegistry.registerFactory(GoogleTestTestSuiteSpec.class, new NamedDomainObjectFactory<GoogleTestTestSuiteSpec>() {
                @Override
                public GoogleTestTestSuiteSpec create(String suiteName) {
                    String path = projectIdentifier.getPath();
                    ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(path, suiteName);
                    FunctionalSourceSet testSuiteSourceSet = createGoogleTestSources(instantiator, suiteName, projectSourceSet, fileResolver);
                    return BaseComponentSpec.create(DefaultGoogleTestTestSuiteSpec.class, id, testSuiteSourceSet, instantiator);
                }
            }, new SimpleModelRuleDescriptor(this.getClass().toString() + ".registerGoogleTestSuiteSpecFactory()"));
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

            for (final GoogleTestTestSuiteSpec suite : testSuites.withType(GoogleTestTestSuiteSpec.class).values()) {
                FunctionalSourceSet suiteSourceSet = ((ComponentSpecInternal) suite).getSources();

                CppSourceSet testSources = suiteSourceSet.maybeCreate("cpp", CppSourceSet.class);
                testSources.getSource().srcDir(String.format("src/%s/%s", suite.getName(), "cpp"));
                testSources.getExportedHeaders().srcDir(String.format("src/%s/headers", suite.getName()));
            }
        }

        @BinaryType
        public void registerGoogleTestSuiteBinaryType(BinaryTypeBuilder<GoogleTestTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteBinary.class);
        }

        @Mutate
        public void createGoogleTestTestBinaries(TestSuiteContainer testSuites, @Path("buildDir") final File buildDir, final ServiceRegistry serviceRegistry, final ITaskFactory taskFactory) {
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
                                testBinary.setComponent(testSuiteSpec);
                                testBinary.setTestedBinary((NativeBinarySpecInternal) testedBinary);
                                testBinary.setNamingScheme(namingScheme);
                                testBinary.setResolver(resolver);

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
