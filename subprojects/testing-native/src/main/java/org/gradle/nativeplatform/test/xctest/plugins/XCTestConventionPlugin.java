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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.language.swift.tasks.CreateSwiftBundle;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.AbstractSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftCorelibXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXcodeXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;
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
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(TestingBasePlugin.class);
        project.getPluginManager().apply(SwiftBasePlugin.class);

        TaskContainer tasks = project.getTasks();

        // Create test suite component
        SwiftXCTestSuite component = createTestSuite(project);

        // Create test suite test task
        final Task testingTask = createTestingTask(project);

        // Configure tasks
        configureTestSuiteBuildingTasks(project);
        configureTestSuiteWithTestedComponentWhenAvailable(project);

        // Create check lifecycle task
        Task check = tasks.getByName("check");

        // Create component lifecycle task
        if (component != null) {
            Task test = tasks.create(component.getName(), new Action<Task>() {
                @Override
                public void execute(Task task) {
                    if (testingTask != null) {
                        task.dependsOn(testingTask);
                    }
                }
            });
            check.dependsOn(test);
        }
    }

    private void configureTestSuiteBuildingTasks(Project project) {
        if (OperatingSystem.current().isMacOsX()) {
            TaskContainer tasks = project.getTasks();

            // Configure compile task
            SwiftCompile compile = (SwiftCompile) tasks.getByName("compileTestSwift");
            compile.getCompilerArgs().addAll(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Arrays.asList("-g", "-F" + frameworkDir.getAbsolutePath());
                }
            }));

            // Add a link task
            LinkMachOBundle link = (LinkMachOBundle) tasks.getByName("linkTest");
            link.getLinkerArgs().set(project.provider(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                    return Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks");
                }
            }));
        }
    }

    private static Task createTestingTask(final Project project) {
        final TaskContainer tasks = project.getTasks();

        Task result = null;

        if (OperatingSystem.current().isMacOsX()) {
            result = tasks.create("xcTest", XcTest.class, new Action<XcTest>() {
                @Override
                public void execute(final XcTest testTask) {
                    CreateSwiftBundle bundle = tasks.withType(CreateSwiftBundle.class).getByName("bundleSwiftTest");
                    DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

                    testTask.getTestBundleDirectory().set(bundle.getOutputDir());
                    testTask.getWorkingDirectory().set(buildDirectory.dir("bundle/test"));
                    testTask.onlyIf(new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task element) {
                            return testTask.getTestBundleDirectory().getAsFile().get().exists();
                        }
                    });
                }
            });
        } else if (OperatingSystem.current().isLinux()){
            result = tasks.create("xcTest", RunTestExecutable.class, new Action<RunTestExecutable>() {
                @Override
                public void execute(final RunTestExecutable testTask) {
                    final InstallExecutable installTask = (InstallExecutable) tasks.getByName("installTest");
                    testTask.setExecutable(installTask.getRunScript());

                    // TODO: Honor changes to build directory
                    testTask.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/xctest").get().getAsFile());
                    testTask.onlyIf(new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task element) {
                            return installTask.getExecutable().exists();
                        }
                    });
                }
            });
        }

        if (result != null) {
            result.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            result.setDescription("Executes XCTest suites");
        }
        return result;
    }

    private static SwiftXCTestSuite createTestSuite(Project project) {
        SwiftXCTestSuite testSuite = null;
        if (OperatingSystem.current().isMacOsX()) {
            // TODO - Reuse logic from Swift*Plugin
            // TODO - component name and extension name aren't the same
            // TODO - should use `src/xctext/swift` as the convention?
            // Add the component extension
            testSuite = project.getObjects().newInstance(DefaultSwiftXcodeXCTestSuite.class,
                "test", project.getConfigurations());
        } else if (OperatingSystem.current().isLinux()) {
            testSuite = project.getObjects().newInstance(DefaultSwiftCorelibXCTestSuite.class,
                "test", project.getConfigurations());
        }

        if (testSuite != null) {
            project.getExtensions().add(SwiftXCTestSuite.class, "xctest", testSuite);
            project.getComponents().add(testSuite);
            project.getComponents().add(testSuite.getDevelopmentBinary());

            // Setup component
            testSuite.getModule().set(GUtil.toCamelCase(project.getName() + "Test"));
        }

        return testSuite;
    }

    private void configureTestSuiteWithTestedComponentWhenAvailable(final Project project) {
        project.getPlugins().withType(SwiftExecutablePlugin.class, configureTestSuiteWithTestedComponent(project));
        project.getPlugins().withType(SwiftLibraryPlugin.class, configureTestSuiteWithTestedComponent(project));
    }

    private static <T> Action<? super T> configureTestSuiteWithTestedComponent(final Project project) {
        return new Action<T>() {
            @Override
            public void execute(T plugin) {
                TaskContainer tasks = project.getTasks();
                SwiftComponent testedComponent = project.getComponents().withType(SwiftComponent.class).getByName("main");
                SwiftXCTestSuite testSuite = project.getExtensions().getByType(SwiftXCTestSuite.class);

                // Connect test suite with tested component
                ((AbstractSwiftXCTestSuite)testSuite).setTestedComponent(testedComponent);

                // Configure test suite compile task from tested component compile task
                SwiftCompile compileMain = tasks.withType(SwiftCompile.class).getByName("compileDebugSwift");
                SwiftCompile compileTest = tasks.withType(SwiftCompile.class).getByName("compileTestSwift");
                compileTest.getModules().from(compileMain.getModuleFile());

                // Test configuration extends main configuration
                testSuite.getImplementationDependencies().extendsFrom(testedComponent.getImplementationDependencies());
                ((Configuration)(testSuite.getDevelopmentBinary().getCompileModules())).getDependencies()
                    .add(new DefaultSelfResolvingDependency((FileCollectionInternal)project.files(((DefaultSwiftBinary)testedComponent.getDevelopmentBinary()).getModuleFile().map(new Transformer<File, RegularFile>() {
                        @Override
                        public File transform(RegularFile regularFile) {
                            return regularFile.getAsFile().getParentFile();
                        }
                    }))));

                // Configure test suite link task from tested component compiled objects
                AbstractLinkTask linkTest = tasks.withType(AbstractLinkTask.class).getByName("linkTest");
                linkTest.source(testedComponent.getDevelopmentBinary().getObjects());

                if (OperatingSystem.current().isLinux()) {
                    tasks.withType(RunTestExecutable.class).getByName("xcTest")
                        .dependsOn(((SwiftExecutable)testSuite.getDevelopmentBinary()).getInstallDirectory());
                }
            }
        };
    }
}
