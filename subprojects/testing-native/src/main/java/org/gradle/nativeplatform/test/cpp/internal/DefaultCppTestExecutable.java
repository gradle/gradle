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

package org.gradle.nativeplatform.test.cpp.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestExecutable;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public class DefaultCppTestExecutable extends DefaultCppBinary implements CppTestExecutable, ConfigurableComponentWithExecutable {
    private final Provider<CppComponent> testedComponent;
    private final RegularFileProperty executableFile;
    private final DirectoryProperty installationDirectory;
    private final Property<InstallExecutable> installTaskProperty;
    private final Property<LinkExecutable> linkTaskProperty;
    private final Property<RunTestExecutable> runTask;
    private final ConfigurableFileCollection outputs;
    private final RegularFileProperty debuggerExecutableFile;

    @Inject
    public DefaultCppTestExecutable(String name, ProjectLayout projectLayout, ObjectFactory objects, FileOperations fileOperations, Provider<String> baseName, boolean debuggable, boolean optimized, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation, Provider<CppComponent> testedComponent, CppPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        super(name, projectLayout, objects, baseName, debuggable, optimized, sourceFiles, componentHeaderDirs, configurations, implementation, targetPlatform, toolChain, platformToolProvider);
        this.testedComponent = testedComponent;
        this.executableFile = projectLayout.fileProperty();
        this.debuggerExecutableFile = projectLayout.fileProperty();
        this.installationDirectory = projectLayout.directoryProperty();
        this.linkTaskProperty = objects.property(LinkExecutable.class);
        this.installTaskProperty = objects.property(InstallExecutable.class);
        this.outputs = fileOperations.files();
        runTask = objects.property(RunTestExecutable.class);
    }

    @Override
    public ConfigurableFileCollection getOutputs() {
        return outputs;
    }

    @Override
    public RegularFileProperty getExecutableFile() {
        return executableFile;
    }

    @Override
    public Property<RegularFile> getDebuggerExecutableFile() {
        return debuggerExecutableFile;
    }

    @Override
    public DirectoryProperty getInstallDirectory() {
        return installationDirectory;
    }

    @Override
    public Property<InstallExecutable> getInstallTask() {
        return installTaskProperty;
    }

    @Override
    public Property<LinkExecutable> getLinkTask() {
        return linkTaskProperty;
    }

    @Override
    public Property<RunTestExecutable> getRunTask() {
        return runTask;
    }

    @Override
    public FileCollection getCompileIncludePath() {
        // TODO: This should be modeled differently, perhaps as a dependency on the implementation configuration
        return super.getCompileIncludePath().plus(getFileOperations().files(new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {
                CppComponent tested = testedComponent.getOrNull();
                if (tested == null) {
                    return getFileOperations().files();
                }
                return ((DefaultCppComponent) tested).getAllHeaderDirs();
            }
        }));
    }
}
