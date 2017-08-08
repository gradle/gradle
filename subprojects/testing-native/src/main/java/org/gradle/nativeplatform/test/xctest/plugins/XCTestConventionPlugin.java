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
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.language.swift.model.SwiftComponent;
import org.gradle.language.swift.plugins.SwiftBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.test.xctest.tasks.CreateXcTestBundle;
import org.gradle.nativeplatform.test.xctest.tasks.XcTest;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;

/**
 * A plugin that sets up the infrastructure for testing native binaries with XCTest test framework. It also adds conventions on top of it.
 *
 *
 */
@Incubating
public class XCTestConventionPlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;
    private final ExecActionFactory execActionFactory;

    private static File cachedSdkPlatformDir;

    @Inject
    public XCTestConventionPlugin(FileOperations fileOperations, ExecActionFactory execActionFactory) {
        this.fileOperations = fileOperations;
        this.execActionFactory = execActionFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        Directory projectDirectory = project.getLayout().getProjectDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        // Add the component extension
        SwiftComponent component = project.getExtensions().create(SwiftComponent.class, "xctest", DefaultSwiftComponent.class, fileOperations);
        component.getSource().from(projectDirectory.dir("src/test/swift"));

        // Add a compile task
        SwiftCompile compile = tasks.create("compileTestSwift", SwiftCompile.class);

        compile.includes(configurations.getByName(SwiftBasePlugin.SWIFT_TEST_IMPORT_PATH));

        FileCollection sourceFiles = component.getSwiftSource();
        compile.source(sourceFiles);

        File frameworkDir = new File(getSdkPlatformPath(), "Developer/Library/Frameworks");

        compile.setCompilerArgs(Lists.newArrayList("-g", "-F" + frameworkDir.getAbsolutePath()));
        compile.setMacros(Collections.<String, String>emptyMap());
        compile.setModuleName(project.getName());

        compile.setObjectFileDir(buildDirectory.dir("test/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = project.getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        LinkExecutable link = tasks.create("linkTest", LinkExecutable.class);
        // TODO - need to set basename
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(configurations.getByName(CppBasePlugin.NATIVE_TEST_LINK));
        link.setLinkerArgs(Lists.newArrayList("-Xlinker", "-bundle", "-F" + frameworkDir.getAbsolutePath(), "-framework", "XCTest", "-Xlinker", "-rpath", "-Xlinker", "@executable_path/../Frameworks", "-Xlinker", "-rpath", "-Xlinker", "@loader_path/../Frameworks"));
        PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
        Provider<RegularFile> exeLocation = buildDirectory.file(toolProvider.getExecutableName("exe/" + project.getName() + "Test"));
        link.setOutputFile(exeLocation);
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);


        Provider<Directory> testBundleDir = buildDirectory.dir("bundle/" + project.getName() + "Test.xctest");
        final CreateXcTestBundle testBundle = tasks.create("createXcTestBundle", CreateXcTestBundle.class);
        testBundle.dependsOn(link);
        testBundle.setExecutableFile(link.getBinaryFile());
        testBundle.setInformationFile(project.file("src/test/resources/Info.plist"));
        testBundle.setOutputDir(testBundleDir);
        testBundle.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return testBundle.getExecutableFile().exists();
            }
        });


        final XcTest test = tasks.create("test", XcTest.class);
        test.dependsOn(testBundle);
        test.setBinResultsDir(buildDirectory.dir("results/test/bin"));
        test.setTestBundleDir(testBundleDir);
        test.setWorkingDir(buildDirectory.dir("bundle"));
        test.getReports().getHtml().setDestination(buildDirectory.dir("reports/test").get());
        test.getReports().getJunitXml().setDestination(buildDirectory.dir("reports/test/xml").get());
        test.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return test.getTestBundleDir().exists();
            }
        });
    }

    private File getSdkPlatformPath() {
        if (cachedSdkPlatformDir == null) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ExecAction execAction = execActionFactory.newExecAction();
            execAction.commandLine("xcrun", "--show-sdk-platform-path");
            execAction.setStandardOutput(outputStream);
            execAction.execute().assertNormalExitValue();

            cachedSdkPlatformDir = new File(outputStream.toString().replace("\n", ""));
        }

        return cachedSdkPlatformDir;
    }
}
