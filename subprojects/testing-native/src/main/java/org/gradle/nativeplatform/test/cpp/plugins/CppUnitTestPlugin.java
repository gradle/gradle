/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.cpp.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestExecutable;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestSuite;
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;


/**
 * A plugin that sets up the infrastructure for testing C++ binaries using a simple test executable.
 *
 * Gradle will create a {@link RunTestExecutable} task that relies on the exit code of the binary.
 *
 * @since 4.4
 */
@Incubating
public class CppUnitTestPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public CppUnitTestPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        project.getPluginManager().apply(NativeTestingBasePlugin.class);

        // Add the unit test and extension
        final DefaultCppTestSuite testComponent = componentFactory.newInstance(CppTestSuite.class, DefaultCppTestSuite.class, "test");
        project.getExtensions().add(CppTestSuite.class, "unitTest", testComponent);
        project.getComponents().add(testComponent);

        testComponent.getBaseName().set(project.getName() + "Test");

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                testComponent.getOperatingSystems().lockNow();
                Set<OperatingSystemFamily> operatingSystemFamilies = testComponent.getOperatingSystems().get();
                if (operatingSystemFamilies.isEmpty()) {
                    throw new IllegalArgumentException("An operating system needs to be specified for the unit test.");
                }

                boolean hasHostOperatingSystem = CollectionUtils.any(operatingSystemFamilies, new Spec<OperatingSystemFamily>() {
                    @Override
                    public boolean isSatisfiedBy(OperatingSystemFamily element) {
                        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(element.getName());
                    }
                });

                if (hasHostOperatingSystem) {
                    String operatingSystemSuffix = "";
                    OperatingSystemFamily operatingSystem = objectFactory.named(OperatingSystemFamily.class, DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName());
                    Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                    Provider<String> group = project.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return project.getGroup().toString();
                        }
                    });

                    Provider<String> version = project.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return project.getVersion().toString();
                        }
                    });

                    AttributeContainer attributesDebug = attributesFactory.mutable();
                    attributesDebug.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    attributesDebug.attribute(DEBUGGABLE_ATTRIBUTE, true);
                    attributesDebug.attribute(OPTIMIZED_ATTRIBUTE, false);

                    // TODO: Fix this naming convention to follow C++ executable/library
                    NativeVariantIdentity debugVariant = new NativeVariantIdentity("debug" + operatingSystemSuffix, testComponent.getBaseName(), group, version, true, false, operatingSystem,
                        null,
                        new DefaultUsageContext("debug" + operatingSystemSuffix + "Runtime", runtimeUsage, attributesDebug));

                    ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);
                    testComponent.addExecutable("executable", debugVariant, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    // TODO: Publishing for test executable?

                    final TaskContainer tasks = project.getTasks();
                    final ProductionCppComponent mainComponent = project.getComponents().withType(ProductionCppComponent.class).findByName("main");
                    if (mainComponent != null) {
                        testComponent.getTestedComponent().set(mainComponent);
                    }

                    testComponent.getBinaries().whenElementKnown(DefaultCppTestExecutable.class, new Action<DefaultCppTestExecutable>() {
                        @Override
                        public void execute(final DefaultCppTestExecutable executable) {
                            if (mainComponent != null) {
                                // TODO: This should be modeled as a kind of dependency vs wiring binaries together directly.
                                mainComponent.getBinaries().whenElementFinalized(new Action<CppBinary>() {
                                    @Override
                                    public void execute(CppBinary testedBinary) {
                                        if (testedBinary != mainComponent.getDevelopmentBinary().get()) {
                                            return;
                                        }

                                        // Setup the dependency on the main binary
                                        // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

                                        // Inherit implementation dependencies
                                        executable.getImplementationDependencies().extendsFrom(((DefaultCppBinary) testedBinary).getImplementationDependencies());

                                        // Configure test binary to link against tested component compiled objects
                                        Dependency linkDependency = project.getDependencies().create(testedBinary.getObjects());
                                        executable.getLinkConfiguration().getDependencies().add(linkDependency);
                                    }
                                });
                            }

                            // TODO: Replace with native test task
                            final RunTestExecutable testTask = tasks.create(executable.getNames().getTaskName("run"), RunTestExecutable.class);
                            testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                            testTask.setDescription("Executes C++ unit tests.");

                            final InstallExecutable installTask = executable.getInstallTask().get();
                            testTask.onlyIf(new Spec<Task>() {
                                @Override
                                public boolean isSatisfiedBy(Task element) {
                                    return executable.getInstallDirectory().get().getAsFile().exists();
                                }
                            });
                            testTask.setExecutable(installTask.getRunScriptFile().get().getAsFile());
                            testTask.dependsOn(testComponent.getTestBinary().get().getInstallDirectory());
                            // TODO: Honor changes to build directory
                            testTask.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/" + executable.getNames().getDirName()).get().getAsFile());
                            executable.getRunTask().set(testTask);
                        }
                    });
                }

                testComponent.getBinaries().realizeNow();
            }
        });
    }
}
