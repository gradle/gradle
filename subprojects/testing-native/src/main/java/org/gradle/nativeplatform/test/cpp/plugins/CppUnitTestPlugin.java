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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.tasks.UnexportMainSymbol;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestExecutable;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestSuite;
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Set;

import static org.gradle.language.nativeplatform.internal.Dimensions.isBuildable;

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
    private final TargetMachineFactory targetMachineFactory;

    @Inject
    public CppUnitTestPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory, TargetMachineFactory targetMachineFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        project.getPluginManager().apply(NativeTestingBasePlugin.class);

        final ProviderFactory providers = project.getProviders();

        // Add the unit test and extension
        final DefaultCppTestSuite testComponent = componentFactory.newInstance(CppTestSuite.class, DefaultCppTestSuite.class, "test");
        project.getExtensions().add(CppTestSuite.class, "unitTest", testComponent);
        project.getComponents().add(testComponent);

        testComponent.getBaseName().set(project.getName() + "Test");

        testComponent.getTargetMachines().convention(Dimensions.getDefaultTargetMachines(targetMachineFactory));
        project.getComponents().withType(ProductionCppComponent.class, new Action<ProductionCppComponent>() {
            @Override
            public void execute(ProductionCppComponent component) {
                if ("main".equals(component.getName())) {
                    testComponent.getTargetMachines().convention(component.getTargetMachines());
                    testComponent.getTestedComponent().set(component);
                }
            }
        });

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                final ProductionCppComponent mainComponent = project.getComponents().withType(ProductionCppComponent.class).findByName("main");

                testComponent.getTargetMachines().finalizeValue();
                Set<TargetMachine> targetMachines = testComponent.getTargetMachines().get();
                validateTargetMachines(targetMachines, mainComponent != null ? mainComponent.getTargetMachines().get() : null);

                Dimensions.unitTestVariants(testComponent.getBaseName(), testComponent.getTargetMachines(), objectFactory, attributesFactory,
                        providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                        variantIdentity -> {
                    if (isBuildable(variantIdentity)) {
                        ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class, variantIdentity.getTargetMachine());
                        // TODO: Removing `debug` from variant name to keep parity with previous Gradle version in tooling models
                        CppTestExecutable testExecutable = testComponent.addExecutable(variantIdentity.getName().replace("debug", ""), variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                        testComponent.getTestBinary().set(testExecutable);
                    }
                });

                // TODO: Publishing for test executable?

                final TaskContainer tasks = project.getTasks();
                testComponent.getBinaries().whenElementKnown(DefaultCppTestExecutable.class, new Action<DefaultCppTestExecutable>() {
                    @Override
                    public void execute(final DefaultCppTestExecutable testExecutable) {
                        if (mainComponent != null) {
                            mainComponent.getBinaries().whenElementFinalized(new Action<CppBinary>() {
                                @Override
                                public void execute(final CppBinary testedBinary) {
                                    if (!isTestedBinary(testExecutable, mainComponent, testedBinary)) {
                                        return;
                                    }

                                    // TODO - move this to a base plugin
                                    // Setup the dependency on the main binary
                                    // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

                                    // Inherit implementation dependencies
                                    testExecutable.getImplementationDependencies().extendsFrom(((DefaultCppBinary) testedBinary).getImplementationDependencies());

                                    // Configure test binary to link against tested component compiled objects
                                    ConfigurableFileCollection testableObjects = project.files();
                                    if (mainComponent instanceof CppApplication) {
                                        TaskProvider<UnexportMainSymbol> unexportMainSymbol = tasks.register(testExecutable.getNames().getTaskName("relocateMainFor"), UnexportMainSymbol.class, new Action<UnexportMainSymbol>() {
                                            @Override
                                            public void execute(UnexportMainSymbol unexportMainSymbol) {
                                                unexportMainSymbol.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("obj/main/for-test"));
                                                unexportMainSymbol.getObjects().from(testedBinary.getObjects());
                                            }
                                        });
                                        // TODO: builtBy unnecessary?
                                        testableObjects.builtBy(unexportMainSymbol);
                                        testableObjects.from(unexportMainSymbol.map(new Transformer<FileCollection, UnexportMainSymbol>() {
                                            @Override
                                            public FileCollection transform(UnexportMainSymbol unexportMainSymbol) {
                                                return unexportMainSymbol.getRelocatedObjects();
                                            }
                                        }));
                                    } else {
                                        testableObjects.from(testedBinary.getObjects());
                                    }
                                    Dependency linkDependency = project.getDependencies().create(testableObjects);
                                    testExecutable.getLinkConfiguration().getDependencies().add(linkDependency);

                                    // Set this as the main test binary if it tests the main development binary
                                    if (testedBinary == mainComponent.getDevelopmentBinary().get() && !testComponent.getTestBinary().isPresent()) {
                                        testComponent.getTestBinary().set(testExecutable);
                                    }
                                }
                            });
                        }

                        // TODO: Replace with native test task
                        final TaskProvider<RunTestExecutable> testTask = tasks.register(testExecutable.getNames().getTaskName("run"), RunTestExecutable.class, new Action<RunTestExecutable>() {
                            @Override
                            public void execute(RunTestExecutable testTask) {
                                testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                                testTask.setDescription("Executes C++ unit tests.");

                                final InstallExecutable installTask = testExecutable.getInstallTask().get();
                                testTask.onlyIf(new Spec<Task>() {
                                    @Override
                                    public boolean isSatisfiedBy(Task element) {
                                        return testExecutable.getInstallDirectory().get().getAsFile().exists();
                                    }
                                });
                                testTask.getInputs().dir(testExecutable.getInstallDirectory());
                                testTask.setExecutable(installTask.getRunScriptFile().get().getAsFile());
                                testTask.dependsOn(testExecutable.getInstallDirectory());
                                // TODO: Honor changes to build directory
                                testTask.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/" + testExecutable.getNames().getDirName()).get().getAsFile());
                            }
                        });
                        testExecutable.getRunTask().set(testTask);
                    }
                });

                testComponent.getBinaries().realizeNow();
            }
        });
    }

    private boolean isTestedBinary(DefaultCppTestExecutable testExecutable, ProductionCppComponent mainComponent, CppBinary testedBinary) {
        // TODO: Make this more intelligent by matching the attributes of the runtime usage on the variant identities
        return testedBinary.getTargetPlatform().getOperatingSystemFamily().getName() == testExecutable.getTargetPlatform().getOperatingSystemFamily().getName()
                && testedBinary.getTargetPlatform().getArchitecture().getName() == testExecutable.getTargetPlatform().getArchitecture().getName()
                && !testedBinary.isOptimized()
                && hasDevelopmentBinaryLinkage(mainComponent, testedBinary);
    }

    private boolean hasDevelopmentBinaryLinkage(ProductionCppComponent mainComponent, CppBinary testedBinary) {
        if (!(testedBinary instanceof ConfigurableComponentWithLinkUsage)) {
            return true;
        }
        ConfigurableComponentWithLinkUsage developmentBinaryWithUsage = (ConfigurableComponentWithLinkUsage) mainComponent.getDevelopmentBinary().get();
        ConfigurableComponentWithLinkUsage testedBinaryWithUsage = (ConfigurableComponentWithLinkUsage)testedBinary;
        return testedBinaryWithUsage.getLinkage() == developmentBinaryWithUsage.getLinkage();
    }

    private void validateTargetMachines(Set<TargetMachine> testTargetMachines, @Nullable Set<TargetMachine> mainTargetMachines) {
        if (testTargetMachines.isEmpty()) {
            throw new IllegalArgumentException("A target machine needs to be specified for the unit test.");
        }

        if (mainTargetMachines != null) {
            for (TargetMachine machine : testTargetMachines) {
                if (!mainTargetMachines.contains(machine)) {
                    throw new IllegalArgumentException("The target machine " + machine.toString() + " was specified for the unit test, but this target machine was not specified on the main component.");
                }
            }
        }
    }
}
