/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Internal;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLibraries;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.metadata.VisualCppMetadata;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class AbstractCppBinaryVisualStudioTargetBinary implements VisualStudioTargetBinary {
    public static final VersionNumber DEFAULT_SDK_VERSION = VersionNumber.parse("8.1");
    public static final VersionNumber DEFAULT_VISUAL_STUDIO_VERSION = VersionNumber.version(14);
    protected final String projectName;
    private final String projectPath;
    private final CppComponent component;
    private final ProjectLayout projectLayout;

    protected AbstractCppBinaryVisualStudioTargetBinary(String projectName, String projectPath, CppComponent component, ProjectLayout projectLayout) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.component = component;
        this.projectLayout = projectLayout;
    }

    @Override
    public LanguageStandard getLanguageStandard() {
        return LanguageStandard.from(getBinary().getCompileTask().get().getCompilerArgs().get());
    }

    @Internal
    abstract CppBinary getBinary();

    @Override
    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public String getComponentName() {
        return projectName;
    }

    @Override
    public String getVisualStudioProjectName() {
        return projectName + getProjectType().getSuffix();
    }

    @Override
    public String getVisualStudioConfigurationName() {
        // TODO: this is terrible
        String buildType = "debug";
        if (getBinary().isOptimized()) {
            buildType = "release";
        }

        String operatingSystemFamilySuffix = Dimensions.createDimensionSuffix(getBinary().getTargetMachine().getOperatingSystemFamily(), component.getBinaries().get().stream().map(CppBinary::getTargetMachine).map(TargetMachine::getOperatingSystemFamily).collect(Collectors.toSet()));
        String architectureSuffix = Dimensions.createDimensionSuffix(getBinary().getTargetMachine().getArchitecture(), component.getBinaries().get().stream().map(CppBinary::getTargetMachine).map(TargetMachine::getArchitecture).collect(Collectors.toSet()));

        return buildType + operatingSystemFamilySuffix + architectureSuffix;
    }

    protected String taskPath(final String taskName) {
        if (":".equals(projectPath)) {
            return ":" + taskName;
        }

        return projectPath + ":" + taskName;
    }

    @Override
    public VersionNumber getVisualStudioVersion() {
        PlatformToolProvider provider = ((DefaultCppBinary) getBinary()).getPlatformToolProvider();
        if (provider.isAvailable()) {
            CompilerMetadata compilerMetadata = provider.getCompilerMetadata(ToolType.CPP_COMPILER);
            if (compilerMetadata instanceof VisualCppMetadata) {
                return ((VisualCppMetadata) compilerMetadata).getVisualStudioVersion();
            }
        }

        // Assume VS 2015
        return DEFAULT_VISUAL_STUDIO_VERSION;
    }

    @Override
    public VersionNumber getSdkVersion() {
        PlatformToolProvider provider = ((DefaultCppBinary) getBinary()).getPlatformToolProvider();
        if (provider.isAvailable()) {
            SystemLibraries systemLibraries = provider.getSystemLibraries(ToolType.CPP_COMPILER);
            if (systemLibraries instanceof WindowsSdkLibraries) {
                WindowsSdkLibraries sdkLibraries = (WindowsSdkLibraries) systemLibraries;
                return sdkLibraries.getSdkVersion();
            }
        }

        // Assume 8.1
        return DEFAULT_SDK_VERSION;
    }

    @Override
    public List<String> getVariantDimensions() {
        return Lists.newArrayList(getBinary().getName());
    }

    @Override
    public FileCollection getSourceFiles() {
        return getBinary().getCppSource();
    }

    @Override
    public FileCollection getResourceFiles() {
        return projectLayout.files();
    }

    @Override
    public FileCollection getHeaderFiles() {
        return component.getHeaderFiles();
    }

    @Override
    public String getCleanTaskPath() {
        return taskPath("clean");
    }

    @Override
    public boolean isDebuggable() {
        return getBinary().isDebuggable();
    }

    @Override
    public List<String> getCompilerDefines() {
        return  new MacroArgsConverter().transform(getBinary().getCompileTask().get().getMacros());
    }

    @Override
    public Set<File> getIncludePaths() {
        return getBinary().getCompileIncludePath().getFiles();
    }
}
