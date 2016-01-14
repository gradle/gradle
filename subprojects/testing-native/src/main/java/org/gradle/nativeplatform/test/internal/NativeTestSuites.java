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
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.SharedLibraryBinary;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteSpec;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;
import java.util.Collection;

import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.executableFileFor;
import static org.gradle.nativeplatform.internal.configure.NativeBinaryRules.installationDirFor;

/**
 * Functions for creation and configuration of native test suites.
 */
public class NativeTestSuites {

    public static void createNativeTestSuiteBinaries(NativeTestSuiteSpec testSuite,
                                                     final Class<? extends NativeTestSuiteBinarySpec> testSuiteBinaryClass,
                                                     final String typeString, final File buildDir, final ServiceRegistry serviceRegistry) {
        for (final NativeBinarySpec testedBinary : testedBinariesOf(testSuite)) {
            if (testedBinary instanceof SharedLibraryBinary) {
                // TODO:DAZ For now, we only create test suites for static library variants
                continue;
            }
            createNativeTestSuiteBinary(testSuite, testSuiteBinaryClass, typeString, testedBinary, buildDir, serviceRegistry);
        }
    }

    private static void createNativeTestSuiteBinary(NativeTestSuiteSpec testSuite,
                                                    Class<? extends NativeTestSuiteBinarySpec> testSuiteBinaryClass,
                                                    String typeString, final NativeBinarySpec testedBinary,
                                                    final File buildDir, ServiceRegistry serviceRegistry) {

        final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, (NativeBinarySpecInternal) testedBinary, typeString);
        final NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);

        testSuite.getBinaries().create(namingScheme.getBinaryName(), testSuiteBinaryClass, new Action<NativeTestSuiteBinarySpec>() {
            @Override
            public void execute(NativeTestSuiteBinarySpec binary) {
                NativeTestSuiteBinarySpecInternal testBinary = (NativeTestSuiteBinarySpecInternal) binary;
                testBinary.setTestedBinary((NativeBinarySpecInternal) testedBinary);
                testBinary.setNamingScheme(namingScheme);
                testBinary.setResolver(resolver);
                testBinary.setToolChain(testedBinary.getToolChain());
                testBinary.getExecutable().setToolChain(testedBinary.getToolChain());
                testBinary.getExecutable().setFile(executableFileFor(testBinary, buildDir));
                testBinary.getInstallation().setDirectory(installationDirFor(testBinary, buildDir));
            }
        });
    }

    public static Collection<NativeBinarySpec> testedBinariesOf(NativeTestSuiteSpec testSuite) {
        return testedBinariesWithType(NativeBinarySpec.class, testSuite);
    }

    public static <S> Collection<S> testedBinariesWithType(Class<S> type, NativeTestSuiteSpec testSuite) {
        NativeComponentSpec spec = testSuite.getTestedComponent();
        if (spec == null) {
            throw new InvalidModelException(String.format("Test suite '%s' doesn't declare component under test. Please specify it with `testing $.components.myComponent`.", testSuite.getName()));
        }
        return spec.getBinaries().withType(type).values();
    }

    private static BinaryNamingScheme namingSchemeFor(NativeTestSuiteSpec testSuite, NativeBinarySpecInternal testedBinary, String typeString) {
        return testedBinary.getNamingScheme()
            .withComponentName(testSuite.getBaseName())
            .withBinaryType(typeString)
            .withRole("executable", true);
    }

}
