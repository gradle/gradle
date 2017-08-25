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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.CreateXcTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 * @since 4.2
 */
@Incubating
public class XCTestConventionPlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;
    private final MacOSSdkPlatformPathLocator sdkPlatformPathLocator;

    @Inject
    public XCTestConventionPlugin(FileOperations fileOperations, MacOSSdkPlatformPathLocator sdkPlatformPathLocator) {
        this.fileOperations = fileOperations;
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
    }

    @Override
    public void apply(final ProjectInternal project) {
        if (!OperatingSystem.current().isMacOsX()) {
            throw new UnsupportedOperationException("'xctest' plugin is only supported on macOS at this stage.");
        }

        project.getPluginManager().apply(SwiftBasePlugin.class);

        // TODO - Add dependency on main component when Swift plugins are applied

        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();
        ProviderFactory providers = project.getProviders();

        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctext/swift` as the convention?
        // Add the component extension
        SwiftXCTestSuite component = project.getExtensions().create(SwiftXCTestSuite.class, "xctest", DefaultSwiftXCTestSuite.class, "test", fileOperations, providers);
        project.getComponents().add(component);
        project.getComponents().add(component.getExecutable());

        // Configure the component
        component.getCompileImportPath().from(configurations.getByName(SwiftBasePlugin.SWIFT_TEST_IMPORT_PATH));
        component.getLinkLibraries().from(configurations.getByName(CppBasePlugin.NATIVE_TEST_LINK));

        // Configure compile task
        SwiftCompile compile = (SwiftCompile) tasks.getByName("compileTestSwift");
        File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
        compile.setCompilerArgs(Lists.newArrayList("-g", "-F" + frameworkDir.getAbsolutePath()));
        compile.setModuleName(project.getName() + "Test");

        NativeToolChain toolChain = compile.getToolChain();
        NativePlatform targetPlatform = compile.getTargetPlatform();

        // TODO - move up to base plugin
        // Add a link task
        LinkExecutable link = tasks.create("linkTest", LinkExecutable.class);
        // TODO - need to set basename from component
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(component.getLinkLibraries());
        link.setLinkerArgs(Lists.newArrayList("-Xlinker", "-bundle", "-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks"));
        PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select((NativePlatformInternal) targetPlatform);
        Provider<RegularFile> exeLocation = buildDirectory.file(toolProvider.getExecutableName("exe/" + project.getName() + "Test"));
        link.setOutputFile(exeLocation);
        link.setTargetPlatform(targetPlatform);
        link.setToolChain(toolChain);

        configureTestedComponent(project);

        // TODO - need to set basename from component
        Provider<Directory> testBundleDir = buildDirectory.dir("bundle/" + project.getName() + "Test.xctest");
        final CreateXcTestBundle testBundle = tasks.create("createXcTestBundle", CreateXcTestBundle.class);
        testBundle.setExecutableFile(link.getBinaryFile());
        // TODO - should be defined on the component
        testBundle.setInformationFile(project.file("src/test/resources/Info.plist"));
        testBundle.setOutputDir(testBundleDir);
        testBundle.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return testBundle.getExecutableFile().exists();
            }
        });

        final XcTest xcTest = tasks.create("xcTest", XcTest.class);
        // TODO - should infer this
        xcTest.dependsOn(testBundle);
        // TODO - should respect changes to build directory
        xcTest.setBinResultsDir(project.file("build/results/test/bin"));
        xcTest.setTestBundleDir(testBundleDir);
        xcTest.setWorkingDir(buildDirectory.dir("bundle"));
        // TODO - should respect changes to reports dir
        // TODO - should respect changes to build dir
        xcTest.getReports().getHtml().setDestination(buildDirectory.dir("reports/test").get().getAsFile());
        xcTest.getReports().getJunitXml().setDestination(buildDirectory.dir("reports/test/xml").get().getAsFile());
        xcTest.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return xcTest.getTestBundleDir().exists();
            }
        });

        Task test = tasks.create("test");
        test.dependsOn(xcTest);

        // TODO - check should depend on test
    }

    private void configureTestedComponent(final Project project) {
        project.getPlugins().withType(SwiftExecutablePlugin.class, new Action<SwiftExecutablePlugin>() {
            @Override
            public void execute(SwiftExecutablePlugin plugin) {
                configureTestedSwiftComponent(project);
            }
        });

        project.getPlugins().withType(SwiftLibraryPlugin.class, new Action<SwiftLibraryPlugin>() {
            @Override
            public void execute(SwiftLibraryPlugin plugin) {
                configureTestedSwiftComponent(project);
            }
        });
    }

    private void configureTestedSwiftComponent(Project project) {
        TaskContainer tasks = project.getTasks();

        SwiftCompile compileMain = tasks.withType(SwiftCompile.class).getByName("compileDebugSwift");
        SwiftCompile compileTest = tasks.withType(SwiftCompile.class).getByName("compileTestSwift");
        compileTest.includes(compileMain.getObjectFileDirectory());

        AbstractLinkTask linkTest = tasks.withType(AbstractLinkTask.class).getByName("linkTest");
        linkTest.source(compileMain.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }
}
