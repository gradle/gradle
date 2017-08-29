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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.LinkBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.CreateXcTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 * @since 4.2
 */
@Incubating
public class XCTestConventionPlugin implements Plugin<ProjectInternal> {
    private final MacOSSdkPlatformPathLocator sdkPlatformPathLocator;
    private final ObjectFactory objectFactory;

    @Inject
    public XCTestConventionPlugin(MacOSSdkPlatformPathLocator sdkPlatformPathLocator, ObjectFactory objectFactory) {
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
        this.objectFactory = objectFactory;
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
        SwiftXCTestSuite component = objectFactory.newInstance(DefaultSwiftXCTestSuite.class, "test", configurations);
        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", component);
        project.getComponents().add(component);
        project.getComponents().add(component.getExecutable());

        // Setup component
        final PropertyState<String> module = component.getModule();
        module.set(GUtil.toCamelCase(project.getName() + "Test"));

        // Configure compile task
        SwiftCompile compile = (SwiftCompile) tasks.getByName("compileTestSwift");
        File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
        compile.getCompilerArgs().set(Lists.newArrayList("-g", "-F" + frameworkDir.getAbsolutePath()));
        compile.setModuleName(project.getName() + "Test");

        // Add a link task
        LinkBundle link = (LinkBundle) tasks.getByName("linkTest");
        link.getLinkerArgs().set(Lists.newArrayList("-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks"));

        configureTestedComponent(project);

        final SwiftBinary binary = component.getExecutable();
        final Names names = Names.of(binary.getName());
        Provider<Directory> testBundleDir = buildDirectory.dir(providers.provider(new Callable<String>() {
            @Override
            public String call() {
                return "bundle/" + names.getDirName() + binary.getModule().get() + ".xctest";
            }
        }));

        final CreateXcTestBundle testBundle = tasks.create("createXcTestBundle", CreateXcTestBundle.class);
        testBundle.setExecutableFile(link.getBinaryFile());
        // TODO - should be defined on the component
        testBundle.setInformationFile(component.getExecutable().getInformationPropertyList());
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
        xcTest.setWorkingDir(project.getProjectDir());
        // TODO - should respect changes to reports dir
        xcTest.getReports().getHtml().setDestination(buildDirectory.dir("reports/test").map(new Transformer<File, Directory>() {
            @Override
            public File transform(Directory directory) {
                return directory.getAsFile();
            }
        }));
        xcTest.getReports().getJunitXml().setDestination(buildDirectory.dir("reports/test/xml").map(new Transformer<File, Directory>() {
            @Override
            public File transform(Directory directory) {
                return directory.getAsFile();
            }
        }));
        xcTest.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return xcTest.getTestBundleDir().exists();
            }
        });

        Task test = tasks.create("test");
        test.dependsOn(xcTest);

        Task check = tasks.getByName("check");
        check.dependsOn(test);
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
        compileTest.includes(compileMain.getObjectFileDir());

        AbstractLinkTask linkTest = tasks.withType(AbstractLinkTask.class).getByName("linkTest");
        linkTest.source(compileMain.getObjectFileDir().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }
}
