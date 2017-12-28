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

package org.gradle.nativeplatform.test.xctest.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.ProductionSwiftComponent;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.language.swift.tasks.UnexportMainSymbol;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.plugins.NativeTestingBasePlugin;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBundle;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestExecutable;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.InstallXCTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XCTest;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 * @since 4.2
 */
@Incubating
public class XCTestConventionPlugin implements Plugin<ProjectInternal> {
    private final MacOSSdkPlatformPathLocator sdkPlatformPathLocator;
    private final ToolChainSelector toolChainSelector;
    private final NativeComponentFactory componentFactory;

    @Inject
    public XCTestConventionPlugin(MacOSSdkPlatformPathLocator sdkPlatformPathLocator, ToolChainSelector toolChainSelector, NativeComponentFactory componentFactory) {
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
        this.toolChainSelector = toolChainSelector;
        this.componentFactory = componentFactory;
    }

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);
        project.getPluginManager().apply(NativeTestingBasePlugin.class);

        // Create test suite component
        final DefaultSwiftXCTestSuite testSuite = createTestSuite(project);

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class);

                // Create test suite executable
                DefaultSwiftXCTestBinary binary;
                if (result.getTargetPlatform().getOperatingSystem().isMacOsX()) {
                    binary = (DefaultSwiftXCTestBinary) testSuite.addBundle("executable", result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                } else {
                    binary = (DefaultSwiftXCTestBinary) testSuite.addExecutable("executable", result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                }
                testSuite.getTestBinary().set(binary);

                // Create test suite test task
                XCTest testingTask = createTestingTask(project);
                binary.getRunTask().set(testingTask);

                // Configure tasks
                configureTestingTask(binary, testingTask);
                configureTestSuiteBuildingTasks((ProjectInternal) project, binary);

                configureTestSuiteWithTestedComponentWhenAvailable(project, testSuite);

                testSuite.getBinaries().realizeNow();
            }
        });
    }

    private void configureTestSuiteBuildingTasks(ProjectInternal project, final DefaultSwiftXCTestBinary binary) {
        if (binary instanceof SwiftXCTestBundle) {
            TaskContainer tasks = project.getTasks();
            final Names names = binary.getNames();
            SwiftCompile compile = binary.getCompileTask().get();

            // TODO - creating a bundle should be done by some general purpose plugin

            // TODO - make this lazy
            DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
            final ModelRegistry modelRegistry = project.getModelRegistry();
            NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);

            // Platform specific arguments
            compile.getCompilerArgs().addAll(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Arrays.asList("-parse-as-library", "-g", "-F" + frameworkDir.getAbsolutePath());
                }
            }));

            // Add a link task
            final LinkMachOBundle link = tasks.create(names.getTaskName("link"), LinkMachOBundle.class);
            link.getLinkerArgs().set(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks");
                }
            }));

            InstallXCTestBundle install = tasks.create(names.getTaskName("install"), InstallXCTestBundle.class);
            install.getBundleBinaryFile().set(link.getBinaryFile());
            install.getInstallDirectory().set(project.getLayout().getBuildDirectory().dir("install/" + names.getDirName()));
            binary.getInstallDirectory().set(install.getInstallDirectory());

            link.source(binary.getObjects());
            link.lib(binary.getLinkLibraries());
            final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
            Provider<RegularFile> exeLocation = project.getLayout().getBuildDirectory().file(project.getProviders().provider(new Callable<String>() {
                @Override
                public String call() {
                    return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getBaseName().get());
                }
            }));
            link.setOutputFile(exeLocation);
            link.setTargetPlatform(currentPlatform);
            link.setToolChain(toolChain);
            link.setDebuggable(binary.isDebuggable());

            binary.getExecutableFile().set(link.getBinaryFile());

            DefaultSwiftXCTestBundle bundle = (DefaultSwiftXCTestBundle) binary;
            bundle.getLinkTask().set(link);
            bundle.getRunScriptFile().set(install.getRunScriptFile());
        } else {
            DefaultSwiftXCTestExecutable executable = (DefaultSwiftXCTestExecutable) binary;
            executable.getRunScriptFile().set(executable.getInstallTask().get().getRunScriptFile());
        }
    }

    private XCTest createTestingTask(final Project project) {
        TaskContainer tasks = project.getTasks();

        XCTest testTask = tasks.create("xcTest", XCTest.class);

        testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        testTask.setDescription("Executes XCTest suites");
        return testTask;
    }

    private void configureTestingTask(SwiftXCTestBinary binary, XCTest testTask) {
        testTask.getTestInstallDirectory().set(binary.getInstallDirectory());
        testTask.getRunScriptFile().set(binary.getRunScriptFile());
        testTask.getWorkingDirectory().set(binary.getInstallDirectory());
    }

    private DefaultSwiftXCTestSuite createTestSuite(final Project project) {
        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctest/swift` as the convention?
        // Add the test suite and extension
        DefaultSwiftXCTestSuite testSuite = componentFactory.newInstance(SwiftXCTestSuite.class, DefaultSwiftXCTestSuite.class, "test");

        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", testSuite);
        project.getComponents().add(testSuite);

        // Setup component
        testSuite.getModule().set(GUtil.toCamelCase(project.getName() + "Test"));

        return testSuite;
    }

    private void configureTestSuiteWithTestedComponentWhenAvailable(final Project project, DefaultSwiftXCTestSuite testSuite) {
        final ProductionSwiftComponent testedComponent = project.getComponents().withType(ProductionSwiftComponent.class).findByName("main");
        if (testedComponent != null) {
            // We know there is a main component but the binaries may not be known at this time.
            configureTestSuiteWithTestedComponent(project, testSuite, testedComponent);
        }
    }

    private static void configureTestSuiteWithTestedComponent(final Project project, final DefaultSwiftXCTestSuite testSuite, final ProductionSwiftComponent testedComponent) {
        final TaskContainer tasks = project.getTasks();

        // Connect test suite with tested component
        testSuite.getTestedComponent().set(testedComponent);

        testSuite.getBinaries().configureEach(new Action<SwiftBinary>() {
            @Override
            public void execute(final SwiftBinary testExecutable) {
                if (testExecutable != testSuite.getTestBinary().get()) {
                    return;
                }

                testedComponent.getBinaries().whenElementFinalized(new Action<SwiftBinary>() {
                    @Override
                    public void execute(SwiftBinary testedBinary) {
                        if (testedBinary != testedComponent.getDevelopmentBinary().get()) {
                            return;
                        }

                        // Test configuration extends main configuration
                        testSuite.getImplementationDependencies().extendsFrom(testedComponent.getImplementationDependencies());
                        project.getDependencies().add(((DefaultSwiftXCTestBinary) testSuite.getTestBinary().get()).getImportPathConfiguration().getName(), project);

                        // Configure test suite link task from tested component compiled objects
                        final AbstractLinkTask linkTest = ((SwiftXCTestBinary) testExecutable).getLinkTask().get();

                        if (testedComponent instanceof SwiftApplication) {
                            final UnexportMainSymbol unexportMainSymbol = tasks.create("relocateMainForTest", UnexportMainSymbol.class);
                            unexportMainSymbol.source(testedBinary.getObjects());
                            linkTest.source(testedBinary.getObjects().filter(new Spec<File>() {
                                @Override
                                public boolean isSatisfiedBy(File objectFile) {
                                    return !objectFile.equals(unexportMainSymbol.getMainObject());
                                }
                            }));

                            linkTest.source(unexportMainSymbol.getObjects());
                        } else {

                            linkTest.source(testedBinary.getObjects());
                        }
                    }
                });

            }
        });
    }
}
