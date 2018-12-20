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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.internal.NativeComponentFactory;
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

import javax.inject.Inject;

import static org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost;

/**
 * A plugin that sets up the infrastructure for testing C++ binaries using a simple test executable.
 *
 * Gradle will create a {@link RunTestExecutable} task that relies on the exit code of the binary.
 *
 * @since 4.4
 */
@Incubating
public class CppUnitTestPlugin implements Plugin<Project> {
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
    public void apply(final Project project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        project.getPluginManager().apply(NativeTestingBasePlugin.class);

        final ProviderFactory providers = project.getProviders();
        final TaskContainer tasks = project.getTasks();

        // Add the unit test and extension
        final DefaultCppTestSuite testComponent = componentFactory.newInstance(CppTestSuite.class, DefaultCppTestSuite.class, "test");
        project.getExtensions().add(CppTestSuite.class, "unitTest", testComponent);
        project.getComponents().add(testComponent);

        testComponent.getBaseName().convention(project.getName() + "Test");
        testComponent.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory));

        final String mainComponentName = "main";
        project.getComponents().withType(ProductionCppComponent.class, component -> {
            if (mainComponentName.equals(component.getName())) {
                testComponent.getTargetMachines().convention(component.getTargetMachines());
                testComponent.getTestedComponent().convention(component);
            }
        });

        testComponent.getTestBinary().convention(project.provider(() -> {
            return testComponent.getBinaries().get().stream()
                    .filter(CppTestExecutable.class::isInstance)
                    .map(CppTestExecutable.class::cast)
                    .findFirst()
                    .orElse(null);
        }));

        testComponent.getBinaries().whenElementKnown(DefaultCppTestExecutable.class, binary -> {
            // TODO: Replace with native test task
            final TaskProvider<RunTestExecutable> testTask = tasks.register(binary.getNames().getTaskName("run"), RunTestExecutable.class, task -> {
                task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                task.setDescription("Executes C++ unit tests.");

                final InstallExecutable installTask = binary.getInstallTask().get();
                task.onlyIf(element -> binary.getInstallDirectory().get().getAsFile().exists());
                task.getInputs().dir(binary.getInstallDirectory());
                task.setExecutable(installTask.getRunScriptFile().get().getAsFile());
                task.dependsOn(binary.getInstallDirectory());
                // TODO: Honor changes to build directory
                task.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/" + binary.getNames().getDirName()).get().getAsFile());
            });
            binary.getRunTask().set(testTask);

            configureTestSuiteWithTestedComponentWhenAvailable(project, testComponent, binary);
        });

        project.afterEvaluate(p -> {
            final CppComponent mainComponent = testComponent.getTestedComponent().getOrNull();
            final SetProperty<TargetMachine> mainTargetMachines = mainComponent != null ? mainComponent.getTargetMachines() : null;
            Dimensions.unitTestVariants(testComponent.getBaseName(), testComponent.getTargetMachines(), mainTargetMachines,
                    objectFactory, attributesFactory,
                    providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                    variantIdentity -> {
                        if (tryToBuildOnHost(variantIdentity)) {
                            ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class, variantIdentity.getTargetMachine());
                            // TODO: Removing `debug` from variant name to keep parity with previous Gradle version in tooling models
                            CppTestExecutable testExecutable = testComponent.addExecutable(variantIdentity.getName().replace("debug", ""), variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                            testComponent.getTestBinary().set(testExecutable);
                        }
                    });
            // TODO: Publishing for test executable?
            testComponent.getBinaries().realizeNow();
        });
    }

    private void configureTestSuiteWithTestedComponentWhenAvailable(Project project, DefaultCppTestSuite testSuite, DefaultCppTestExecutable testExecutable) {
        CppComponent target = testSuite.getTestedComponent().getOrNull();
        if (!(target instanceof ProductionCppComponent)) {
            return;
        }
        final ProductionCppComponent testedComponent = (ProductionCppComponent) target;

        final TaskContainer tasks = project.getTasks();
        testedComponent.getBinaries().whenElementFinalized(testedBinary -> {
            if (testedBinary != testedComponent.getDevelopmentBinary().get()) {
                return;
            }
            // TODO - move this to a base plugin
            // Setup the dependency on the main binary
            // This should all be replaced by a single dependency that points at some "testable" variants of the main binary

            // Inherit implementation dependencies
            testExecutable.getImplementationDependencies().extendsFrom(((DefaultCppBinary) testedBinary).getImplementationDependencies());

            // Configure test binary to link against tested component compiled objects
            ConfigurableFileCollection testableObjects = project.files();
            if (target instanceof CppApplication) {
                TaskProvider<UnexportMainSymbol> unexportMainSymbol = tasks.register(testExecutable.getNames().getTaskName("relocateMainFor"), UnexportMainSymbol.class, task -> {
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("obj/main/for-test"));
                    task.getObjects().from(testedBinary.getObjects());
                });
                // TODO: builtBy unnecessary?
                testableObjects.builtBy(unexportMainSymbol);
                testableObjects.from(unexportMainSymbol.map(task -> task.getRelocatedObjects()));
            } else {
                testableObjects.from(testedBinary.getObjects());
            }
            Dependency linkDependency = project.getDependencies().create(testableObjects);
            testExecutable.getLinkConfiguration().getDependencies().add(linkDependency);
        });
    }
}
