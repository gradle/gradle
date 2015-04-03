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

package org.gradle.nativeplatform.test.cunit.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.internal.DefaultCSourceSet;
import org.gradle.language.c.plugins.CLangPlugin;
import org.gradle.language.nativeplatform.internal.DefaultPreprocessingTool;
import org.gradle.model.*;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteBinary;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.tasks.GenerateCUnitLauncher;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
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

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        private static final String CUNIT_LAUNCHER_SOURCE_SET = "cunitLauncher";

        // TODO:DAZ Test suites should belong to ComponentSpecContainer, and we could rely on more conventions from the base plugins
        @Defaults
        public void createCUnitTestSuitePerComponent(TestSuiteContainer testSuites, NamedDomainObjectSet<NativeComponentSpec> components, ProjectSourceSet projectSourceSet, ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            for (NativeComponentSpec component : components) {
                testSuites.add(createCUnitTestSuite(component, instantiator, projectSourceSet, fileResolver));
            }
        }

        private CUnitTestSuiteSpec createCUnitTestSuite(final NativeComponentSpec testedComponent, Instantiator instantiator, ProjectSourceSet projectSourceSet, FileResolver fileResolver) {
            String suiteName = String.format("%sTest", testedComponent.getName());
            String path = testedComponent.getProjectPath();
            ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(path, suiteName);
            FunctionalSourceSet testSuiteSourceSet = createCUnitSources(instantiator, suiteName, projectSourceSet, fileResolver);
            CUnitTestSuiteSpec testSuiteSpec = BaseComponentSpec.create(DefaultCUnitTestSuiteSpec.class, id, testSuiteSourceSet, instantiator);
            testSuiteSpec.setTestedComponent(testedComponent);
            return testSuiteSpec;
        }

        private FunctionalSourceSet createCUnitSources(final Instantiator instantiator, final String suiteName, ProjectSourceSet projectSourceSet, final FileResolver fileResolver) {
            final FunctionalSourceSet functionalSourceSet = instantiator.newInstance(DefaultFunctionalSourceSet.class, suiteName, instantiator, projectSourceSet);
            functionalSourceSet.registerFactory(CSourceSet.class, new NamedDomainObjectFactory<CSourceSet>() {
                public CSourceSet create(String name) {
                    return BaseLanguageSourceSet.create(DefaultCSourceSet.class, name, suiteName, fileResolver, instantiator);
                }
            });
            return functionalSourceSet;
        }

        @Finalize
        public void configureCUnitTestSuiteSources(TestSuiteContainer testSuites, @Path("buildDir") File buildDir) {

            for (final CUnitTestSuiteSpec suite : testSuites.withType(CUnitTestSuiteSpec.class)) {
                FunctionalSourceSet suiteSourceSet = ((ComponentSpecInternal) suite).getSources();
                CSourceSet launcherSources = suiteSourceSet.maybeCreate(CUNIT_LAUNCHER_SOURCE_SET, CSourceSet.class);
                File baseDir = new File(buildDir, String.format("src/%s/cunitLauncher", suite.getName()));
                launcherSources.getSource().srcDir(new File(baseDir, "c"));
                launcherSources.getExportedHeaders().srcDir(new File(baseDir, "headers"));

                CSourceSet testSources = suiteSourceSet.maybeCreate("c", CSourceSet.class);
                testSources.getSource().srcDir(String.format("src/%s/%s", suite.getName(), "c"));
                testSources.getExportedHeaders().srcDir(String.format("src/%s/headers", suite.getName()));

                testSources.lib(launcherSources);
            }
        }

        @Mutate
        public void createCUnitLauncherTasks(TaskContainer tasks, TestSuiteContainer testSuites) {
            for (final CUnitTestSuiteSpec suite : testSuites.withType(CUnitTestSuiteSpec.class)) {

                String taskName = suite.getName() + "CUnitLauncher";
                GenerateCUnitLauncher skeletonTask = tasks.create(taskName, GenerateCUnitLauncher.class);

                CSourceSet launcherSources = findLaucherSources(suite);
                skeletonTask.setSourceDir(launcherSources.getSource().getSrcDirs().iterator().next());
                skeletonTask.setHeaderDir(launcherSources.getExportedHeaders().getSrcDirs().iterator().next());
                launcherSources.builtBy(skeletonTask);
            }
        }

        private CSourceSet findLaucherSources(CUnitTestSuiteSpec suite) {
            return suite.getSource().withType(CSourceSet.class).matching(new Spec<CSourceSet>() {
                public boolean isSatisfiedBy(CSourceSet element) {
                    return element.getName().equals(CUNIT_LAUNCHER_SOURCE_SET);
                }
            }).iterator().next();
        }

        @Mutate
        public void createCUnitTestBinaries(final BinaryContainer binaries, TestSuiteContainer testSuites, @Path("buildDir") File buildDir, ServiceRegistry serviceRegistry, ITaskFactory taskFactory) {
            for (final CUnitTestSuiteSpec cUnitTestSuite : testSuites.withType(CUnitTestSuiteSpec.class)) {
                for (NativeBinarySpec testedBinary : cUnitTestSuite.getTestedComponent().getBinaries().withType(NativeBinarySpec.class)) {

                    if (testedBinary instanceof SharedLibraryBinary) {
                        // TODO:DAZ For now, we only create test suites for static library variants
                        continue;
                    }

                    DefaultCUnitTestSuiteBinary testBinary = createTestBinary(serviceRegistry, cUnitTestSuite, testedBinary, taskFactory);

                    configure(testBinary, buildDir);

                    cUnitTestSuite.getBinaries().add(testBinary);
                    binaries.add(testBinary);
                }
            }
        }

        private DefaultCUnitTestSuiteBinary createTestBinary(ServiceRegistry serviceRegistry, CUnitTestSuiteSpec cUnitTestSuite, NativeBinarySpec testedBinary, ITaskFactory taskFactory) {
            BinaryNamingScheme namingScheme = new DefaultBinaryNamingSchemeBuilder(((NativeBinarySpecInternal) testedBinary).getNamingScheme())
                    .withComponentName(cUnitTestSuite.getBaseName())
                    .withTypeString("CUnitExe").build();

            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            return DefaultCUnitTestSuiteBinary.create(cUnitTestSuite, (NativeBinarySpecInternal) testedBinary, namingScheme, resolver, instantiator, taskFactory);
        }

        private void configure(DefaultCUnitTestSuiteBinary testBinary, File buildDir) {
            BinaryNamingScheme namingScheme = testBinary.getNamingScheme();
            PlatformToolProvider toolProvider = testBinary.getPlatformToolProvider();
            File binaryOutputDir = new File(new File(buildDir, "binaries"), namingScheme.getOutputDirectoryBase());
            String baseName = testBinary.getComponent().getBaseName();

            testBinary.setExecutableFile(new File(binaryOutputDir, toolProvider.getExecutableName(baseName)));

            ((ExtensionAware) testBinary).getExtensions().create("cCompiler", DefaultPreprocessingTool.class);
        }
    }
}
