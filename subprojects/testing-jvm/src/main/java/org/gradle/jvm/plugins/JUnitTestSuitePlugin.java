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

import com.beust.jcommander.internal.Lists;
import org.gradle.api.*;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithDependencies;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.*;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.java.plugins.JavaLanguagePlugin;
import org.gradle.model.*;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

/**
 * This plugin adds support for execution of JUnit test suites to the Java software model.
 *
 * @since 2.11
 */
@Incubating
public class JUnitTestSuitePlugin implements Plugin<Project> {

    private final ModelRegistry modelRegistry;

    @Inject
    public JUnitTestSuitePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(TestingModelBasePlugin.class);
        project.getPluginManager().apply(JvmComponentPlugin.class);
        applyJUnitTestSuiteRules();
    }

    private void applyJUnitTestSuiteRules() {
        modelRegistry.getRoot().applyTo(allDescendants(withType(JUnitTestSuiteSpec.class)), JUnitTestSuiteRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class PluginRules extends RuleSource {

        @Mutate
        public void registerCompileClasspathConfigurer(LanguageTransformContainer languages, final ServiceRegistry serviceRegistry, final ModelSchemaStore schemaStore) {
            JavaLanguagePlugin.registerPlatformJavaCompileConfig(languages, new JvmTestSuiteCompileClasspathConfig(serviceRegistry, schemaStore));
        }

        @ComponentType
        public void register(ComponentTypeBuilder<JUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteSpec.class);
        }

        @BinaryType
        public void registerJUnitBinary(BinaryTypeBuilder<JUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteBinarySpec.class);
        }

        @Defaults
        public void createTestSuiteTasks(
                                  final ModelMap<JUnitTestSuiteBinarySpec> binaries,
                                  final @Path("buildDir") File buildDir,
                                  final ServiceRegistry registry,
                                  final ModelSchemaStore schemaStore) {
            binaries.afterEach(new Action<JUnitTestSuiteBinarySpec>() {
                @Override
                public void execute(JUnitTestSuiteBinarySpec binary) {
                    final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
                    JvmTestSuites.createJvmTestSuiteTasks(binary, jvmAssembly, registry, schemaStore, buildDir);
                }
            });
        }

        /**
         * Create binaries for test suites.
         */
        @ComponentBinaries
        void createJUnitComponentBinaries(ModelMap<BinarySpec> testBinaries,
                                          ServiceRegistry registry,
                                          PlatformResolvers platformResolver,
                                          JUnitTestSuiteSpec testSuite,
                                          JavaToolChainRegistry toolChains) {
            final String jUnitVersion = testSuite.getJUnitVersion();
            final DependencySpecContainer dependencies = testSuite.getDependencies();
            addJUnitDependencyTo(dependencies, jUnitVersion);
            JvmTestSuites.createJvmTestSuiteBinaries(testBinaries, registry, testSuite, JUnitTestSuiteBinarySpec.class, toolChains, platformResolver, new Action<JUnitTestSuiteBinarySpec>() {
                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    setDependenciesOf(jUnitTestSuiteBinarySpec, dependencies);
                }
            });
        }

        @Mutate
        void attachBinariesToCheckLifecycle(@Path("tasks.check") Task checkTask, ModelMap<JUnitTestSuiteBinarySpec> binaries) {
            JvmTestSuites.attachBinariesToCheckLifecycle(checkTask, binaries);
        }

        private void setDependenciesOf(JUnitTestSuiteBinarySpec binary, DependencySpecContainer dependencies) {
            ((WithDependencies) binary).setDependencies(Lists.newArrayList(dependencies.getDependencies()));
        }

        private void addJUnitDependencyTo(DependencySpecContainer dependencies, String jUnitVersion) {
            dependencies.group("junit").module("junit").version(jUnitVersion);
        }

    }

}
