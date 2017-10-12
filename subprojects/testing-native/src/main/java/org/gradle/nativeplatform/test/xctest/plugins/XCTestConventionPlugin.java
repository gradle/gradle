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

import com.google.common.collect.Iterables;
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
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.language.swift.plugins.SwiftLibraryPlugin;
import org.gradle.language.swift.tasks.CreateSwiftBundle;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.DefaultSwiftXCTestSuite;
import org.gradle.nativeplatform.test.xctest.internal.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
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
    private final ObjectFactory objectFactory;

    @Inject
    public XCTestConventionPlugin(MacOSSdkPlatformPathLocator sdkPlatformPathLocator, ObjectFactory objectFactory) {
        this.sdkPlatformPathLocator = sdkPlatformPathLocator;
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        // TODO - Add dependency on main component when Swift plugins are applied

        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        // TODO - Reuse logic from Swift*Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use `src/xctext/swift` as the convention?
        // Add the component extension
        SwiftXCTestSuite component = objectFactory.newInstance(DefaultSwiftXCTestSuite.class, "test", configurations);
        project.getExtensions().add(SwiftXCTestSuite.class, "xctest", component);
        project.getComponents().add(component);
        project.getComponents().add(component.getBundle());

        // Setup component
        final Property<String> module = component.getModule();
        module.set(GUtil.toCamelCase(project.getName() + "Test"));

        // Configure compile task
        SwiftCompile compile = (SwiftCompile) tasks.getByName("compileTestSwift");
        // TODO - Avoid evaluating the arguments here
        final List<String> currentCompilerArguments = compile.getCompilerArgs().getOrElse(Collections.<String>emptyList());
        compile.getCompilerArgs().set(project.provider(new Callable<List<String>>() {
            @Override
            public List<String> call() throws Exception {
                File frameworkDir = new File(sdkPlatformPathLocator.find(), "Developer/Library/Frameworks");
                return Lists.newArrayList(Iterables.concat(
                    Arrays.asList("-g", "-F" + frameworkDir.getAbsolutePath()),
                    currentCompilerArguments));
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

        configureTestedComponent(project);

        CreateSwiftBundle bundle = (CreateSwiftBundle) tasks.getByName("bundleSwiftTest");

        final XcTest xcTest = tasks.create("xcTest", XcTest.class);
        // TODO - should respect changes to build directory
        xcTest.setBinResultsDir(project.file("build/results/test/bin"));
        xcTest.setTestBundleDir(bundle.getOutputDir());
        xcTest.setWorkingDir(buildDirectory.dir("bundle/test"));
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

        if (OperatingSystem.current().isMacOsX()) {
            test.dependsOn(xcTest);
        }

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
