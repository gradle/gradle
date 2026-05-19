/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.test.internal;

import org.gradle.api.Action;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativeComponents;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.Collection;

import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.executableFileFor;
import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.installationDirFor;

/**
 * Functions for creation and configuration of native test suites.
 */
@SuppressWarnings("deprecation")
public class NativeTestSuites {

    public static <S extends org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec> void createNativeTestSuiteBinaries(org.gradle.model.ModelMap<S> binaries,
                                                     org.gradle.nativeplatform.test.NativeTestSuiteSpec testSuite,
                                                     final Class<S> testSuiteBinaryClass,
                                                     final String typeString, final File buildDir, final ServiceRegistry serviceRegistry) {
        for (final org.gradle.nativeplatform.NativeBinarySpec testedBinary : testedBinariesOf(testSuite)) {
            if (testedBinary instanceof org.gradle.nativeplatform.SharedLibraryBinary) {
                // For now, we only create test suites for static library variants
                continue;
            }
            createNativeTestSuiteBinary(binaries, testSuite, testSuiteBinaryClass, typeString, testedBinary, buildDir, serviceRegistry);
        }
    }

    private static <S extends org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec> void createNativeTestSuiteBinary(org.gradle.model.ModelMap<S> binaries,
                                                    org.gradle.nativeplatform.test.NativeTestSuiteSpec testSuite,
                                                    Class<S> testSuiteBinaryClass,
                                                    String typeString,
                                                    final org.gradle.nativeplatform.NativeBinarySpec testedBinary,
                                                    final File buildDir, ServiceRegistry serviceRegistry) {

        final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, (NativeBinarySpecInternal) testedBinary, typeString);
        final NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);

        binaries.create(namingScheme.getBinaryName(), testSuiteBinaryClass, new Action<S>() {
            @Override
            public void execute(S binary) {
                final NativeTestSuiteBinarySpecInternal testBinary = (NativeTestSuiteBinarySpecInternal) binary;
                testBinary.setTestedBinary((NativeBinarySpecInternal) testedBinary);
                testBinary.setNamingScheme(namingScheme);
                testBinary.setResolver(resolver);
                testBinary.setToolChain(testedBinary.getToolChain());
                org.gradle.nativeplatform.NativeExecutableFileSpec executable = testBinary.getExecutable();
                org.gradle.nativeplatform.NativeInstallationSpec installation = testBinary.getInstallation();
                executable.setToolChain(testedBinary.getToolChain());
                executable.setFile(executableFileFor(testBinary, buildDir));
                installation.setDirectory(installationDirFor(testBinary, buildDir));
                NativeComponents.createInstallTask(testBinary, installation, executable, namingScheme);
                NativeComponents.createExecutableTask(testBinary, testBinary.getExecutableFile());
                createRunTask(testBinary, namingScheme.getTaskName("run"));
            }
        });
    }

    private static void createRunTask(final NativeTestSuiteBinarySpecInternal testBinary, String name) {
        testBinary.getTasks().create(name, RunTestExecutable.class, new Action<RunTestExecutable>() {
            @Override
            public void execute(RunTestExecutable runTask) {
                runTask.setDescription("Runs the " + testBinary);
                testBinary.getTasks().add(runTask);
            }
        });
    }
    public static Collection<org.gradle.nativeplatform.NativeBinarySpec> testedBinariesOf(org.gradle.nativeplatform.test.NativeTestSuiteSpec testSuite) {
        return testedBinariesWithType(org.gradle.nativeplatform.NativeBinarySpec.class, testSuite);
    }

    public static <S> Collection<S> testedBinariesWithType(Class<S> type, org.gradle.nativeplatform.test.NativeTestSuiteSpec testSuite) {
        org.gradle.platform.base.VariantComponentSpec spec = (org.gradle.platform.base.VariantComponentSpec) testSuite.getTestedComponent();
        if (spec == null) {
            throw new org.gradle.platform.base.InvalidModelException(String.format("Test suite '%s' doesn't declare component under test. Please specify it with `testing $.components.myComponent`.", testSuite.getName()));
        }
        return spec.getBinaries().withType(type).values();
    }

    private static BinaryNamingScheme namingSchemeFor(org.gradle.nativeplatform.test.NativeTestSuiteSpec testSuite, NativeBinarySpecInternal testedBinary, String typeString) {
        return testedBinary.getNamingScheme()
            .withComponentName(testSuite.getBaseName())
            .withBinaryType(typeString)
            .withRole("executable", true);
    }

    public static <S extends org.gradle.nativeplatform.test.NativeTestSuiteSpec> void createConventionalTestSuites(org.gradle.testing.base.TestSuiteContainer testSuites, org.gradle.model.ModelMap<org.gradle.nativeplatform.NativeComponentSpec> components, Class<S> testSuiteSpecClass) {
        for (final org.gradle.nativeplatform.NativeComponentSpec component : components.values()) {
            final String suiteName = component.getName() + "Test";
            testSuites.create(suiteName, testSuiteSpecClass, new Action<S>() {
                @Override
                public void execute(S testSuite) {
                    testSuite.testing(component);
                }
            });
        }
    }
}
