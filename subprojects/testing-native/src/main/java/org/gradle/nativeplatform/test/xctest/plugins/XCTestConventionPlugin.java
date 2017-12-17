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
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.language.swift.tasks.UnexportMainSymbol;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestBinary;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.InstallXCTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XCTest;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.testing.base.plugins.TestingBasePlugin;
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

    @Inject
    public XCTestConventionPlugin(MacOSSdkPlatformPathLocator sdkPlatformPathLocator) {
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
    }

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(TestingBasePlugin.class);
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final TaskContainer tasks = project.getTasks();

        // Create test suite component
        final DefaultSwiftXCTestSuite testSuite = createTestSuite(project);

        // Create test suite test task
        final XCTest testingTask = createTestingTask(project);

        // Create testSuite lifecycle task
        Task test = tasks.create(testSuite.getName());
        test.dependsOn(testingTask);

        // Swift is only supported on macOS and Linux right now
        if (OperatingSystem.current().isMacOsX() || OperatingSystem.current().isLinux()) {
            // Wire to check lifecycle task
            Task check = tasks.getByName("check");
            check.dependsOn(test);
        }

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                // Create test suite executable
                SwiftXCTestBinary binary = testSuite.createExecutable();
                testSuite.getDevelopmentBinary().set(binary);

                // Configure tasks
                configureTestingTask(testSuite, testingTask);
                configureTestSuiteBuildingTasks((ProjectInternal) project, testSuite);

                configureTestSuiteWithTestedComponentWhenAvailable(project, testSuite);

                testSuite.getBinaries().realizeNow();
            }
        });
    }

    private void configureTestSuiteBuildingTasks(ProjectInternal project, SwiftXCTestSuite testSuite) {
        TaskContainer tasks = project.getTasks();
        final SwiftXCTestBinary binary = testSuite.getTestExecutable().get();
        final Names names = Names.of(binary.getName());
        SwiftCompile compile = binary.getCompileTask().get();
        final AbstractLinkTask link;

        // TODO - make this lazy
        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        final ModelRegistry modelRegistry = project.getModelRegistry();
        NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);

        if (OperatingSystem.current().isMacOsX()) {
            // Platform specific arguments
            compile.getCompilerArgs().addAll(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Arrays.asList("-parse-as-library", "-g", "-F" + frameworkDir.getAbsolutePath());
                }
            }));

            // Add a link task
            link = tasks.create(names.getTaskName("link"), LinkMachOBundle.class);
            link.getLinkerArgs().set(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks");
                }
            }));

            InstallXCTestBundle install = tasks.create(names.getTaskName("install"), InstallXCTestBundle.class);
            install.getBundleBinaryFile().set(binary.getExecutableTestFile());
            install.getInstallDirectory().set(project.getLayout().getBuildDirectory().dir("install/" + names.getDirName()));
            ((DefaultSwiftXCTestBinary)binary).getInstallDirectory().set(install.getInstallDirectory());
            ((DefaultSwiftXCTestBinary)binary).getRunScriptFile().set(install.getRunScriptFile());
        } else {
            link = tasks.create(names.getTaskName("link"), LinkExecutable.class);

            final InstallExecutable install = tasks.create(names.getTaskName("install"), InstallExecutable.class);
            install.setPlatform(currentPlatform);
            install.setToolChain(toolChain);
            install.getInstallDirectory().set(project.getLayout().getBuildDirectory().dir("install/" + names.getDirName()));
            install.getSourceFile().set(binary.getExecutableTestFile());
            install.lib(binary.getRuntimeLibraries());
            ((DefaultSwiftXCTestBinary)binary).getInstallDirectory().set(install.getInstallDirectory());
            ((DefaultSwiftXCTestBinary)binary).getRunScriptFile().set(install.getRunScriptFile());
        }

        link.source(binary.getObjects());
        link.lib(binary.getLinkLibraries());
        final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
        Provider<RegularFile> exeLocation = project.getLayout().getBuildDirectory().file(project.getProviders().provider(new Callable<String>() {
            @Override
            public String call() {
                return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getModule().get());
            }
        }));
        link.setOutputFile(exeLocation);
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);
        link.setDebuggable(binary.isDebuggable());

        ((DefaultSwiftXCTestBinary)binary).getExecutableTestFile().set(link.getBinaryFile());
        ((DefaultSwiftXCTestBinary)binary).getLinkTask().set(link);
    }

    private static XCTest createTestingTask(final Project project) {
        TaskContainer tasks = project.getTasks();

        XCTest testTask = tasks.create("xcTest", XCTest.class);

        testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        testTask.setDescription("Executes XCTest suites");
        return testTask;
    }

    private static void configureTestingTask(SwiftXCTestSuite testSuite, XCTest testTask) {
        SwiftXCTestBinary binary = testSuite.getTestExecutable().get();
        testTask.getTestInstallDirectory().set(binary.getInstallDirectory());
        testTask.getRunScriptFile().set(binary.getRunScriptFile());
        testTask.getWorkingDirectory().set(binary.getInstallDirectory());
    }

    private static DefaultSwiftXCTestSuite createTestSuite(final Project project) {
        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctext/swift` as the convention?
        // Add the component extension
        DefaultSwiftXCTestSuite testSuite = project.getObjects().newInstance(DefaultSwiftXCTestSuite.class, "test", project.getConfigurations());
        testSuite.getBinaries().whenElementKnown(new Action<SwiftBinary>() {
            @Override
            public void execute(SwiftBinary binary) {
                project.getComponents().add(binary);
            }
        });

        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", testSuite);
        project.getComponents().add(testSuite);

        // Setup component
        testSuite.getModule().set(GUtil.toCamelCase(project.getName() + "Test"));

        return testSuite;
    }

    private void configureTestSuiteWithTestedComponentWhenAvailable(final Project project, DefaultSwiftXCTestSuite testSuite) {
        final SwiftComponent testedComponent = project.getComponents().withType(SwiftComponent.class).findByName("main");
        if (testedComponent != null) {
            // We know there is a main component but the binaries may not be known at this time.
            configureTestSuiteWithTestedComponent(project, testSuite, testedComponent);
        }
    }

    private static void configureTestSuiteWithTestedComponent(final Project project, final DefaultSwiftXCTestSuite testSuite, final SwiftComponent testedComponent) {
        final TaskContainer tasks = project.getTasks();

        // Connect test suite with tested component
        testSuite.getTestedComponent().set(testedComponent);

        testSuite.getBinaries().configureEach(new Action<SwiftBinary>() {
            @Override
            public void execute(final SwiftBinary testExecutable) {
                if (testExecutable != testSuite.getTestExecutable().get()) {
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
                        project.getDependencies().add(((DefaultSwiftXCTestBinary) testSuite.getDevelopmentBinary().get()).getImportPathConfiguration().getName(), project);

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
