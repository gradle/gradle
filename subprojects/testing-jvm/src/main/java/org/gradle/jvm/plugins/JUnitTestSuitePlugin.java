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
package org.gradle.jvm.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.JvmTestSuiteBinarySpecInternal;
import org.gradle.jvm.test.internal.JvmTestSuiteRules;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.model.Defaults;
import org.gradle.model.Each;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.Validate;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.DefaultModuleDependencySpec;
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

/**
 * This plugin adds support for execution of JUnit test suites to the Java software model.
 *
 * @since 2.11
 */
@Incubating
@Deprecated
public class JUnitTestSuitePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        DeprecationLogger.deprecatePlugin("junit-test-suite")
            .willBeRemovedInGradle7()
            .withUpgradeGuideSection(6, "upgrading_jvm_plugins")
            .nagUser();
        project.getPluginManager().apply(TestingModelBasePlugin.class);
        project.getPluginManager().apply(JvmComponentPlugin.class);
        project.getPluginManager().apply(JvmTestSuiteBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class PluginRules extends RuleSource {

        @ComponentType
        public void register(TypeBuilder<JUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteSpec.class);
            builder.internalView(HasIntermediateOutputsComponentSpec.class);
        }

        @ComponentType
        public void registerJUnitBinary(TypeBuilder<JUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteBinarySpec.class);
            builder.internalView(JvmTestSuiteBinarySpecInternal.class);
        }

        @ComponentBinaries
        public static void createJvmTestSuiteBinaries(ModelMap<BinarySpec> testBinaries,
                                                      PlatformResolvers platformResolver,
                                                      JvmTestSuiteSpec testSuite,
                                                      JavaToolChainRegistry toolChains) {
            JvmTestSuiteRules.createJvmTestSuiteBinaries(testBinaries, platformResolver, testSuite, toolChains, JUnitTestSuiteBinarySpec.class);
        }

        @Validate
        void validateJUnitVersion(@Each JUnitTestSuiteSpec jUnitTestSuiteSpec) {
            if (jUnitTestSuiteSpec.getjUnitVersion() == null) {
                throw new InvalidModelException(
                    String.format("Test suite '%s' doesn't declare JUnit version. Please specify it with `jUnitVersion '4.13'` for example.", jUnitTestSuiteSpec.getName()));
            }
        }

        @Defaults
        void configureBinaryJUnitVersion(@Each JUnitTestSuiteBinarySpec testSuiteBinary) {
            JUnitTestSuiteSpec testSuite = testSuiteBinary.getTestSuite();
            String jUnitVersion = testSuite.getjUnitVersion();
            testSuiteBinary.setjUnitVersion(jUnitVersion);
            testSuiteBinary.getDependencies().add(new DefaultModuleDependencySpec("junit", "junit", jUnitVersion));
        }
    }
}
