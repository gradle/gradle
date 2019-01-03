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
import org.gradle.api.internal.file.collections.ImmutableFileCollection;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLibraries;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.metadata.VisualCppMetadata;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract public class AbstractCppBinaryVisualStudioTargetBinary implements VisualStudioTargetBinary {
    protected final String projectName;
    private final String projectPath;
    private final CppComponent component;
    private final VersionNumber visualStudioVersion;
    private final VersionNumber windowsSdkVersion;

    protected AbstractCppBinaryVisualStudioTargetBinary(String projectName, String projectPath, CppComponent component, CppBinary binary) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.component = component;
        PlatformToolProvider provider = ((DefaultCppBinary) binary).getPlatformToolProvider();
        CompilerMetadata compilerMetadata = provider.getCompilerMetadata(ToolType.CPP_COMPILER);
        if (compilerMetadata instanceof VisualCppMetadata) {
            visualStudioVersion = ((VisualCppMetadata) compilerMetadata).getVisualStudioVersion();
        } else {
            // Assume VS 2015
            visualStudioVersion = VersionNumber.version(14);
        }
        SystemLibraries systemLibraries = provider.getSystemLibraries(ToolType.CPP_COMPILER);
        if (systemLibraries instanceof WindowsSdkLibraries) {
            WindowsSdkLibraries sdkLibraries = (WindowsSdkLibraries) systemLibraries;
            windowsSdkVersion = sdkLibraries.getSdkVersion();
        } else {
            // Assume 8.1
            windowsSdkVersion = VersionNumber.parse("8.1");
        }
    }

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

        String operatingSystemFamilySuffix = Dimensions.createDimensionSuffix(getBinary().getTargetPlatform().getOperatingSystemFamily(), component.getBinaries().get().stream().map(CppBinary::getTargetPlatform).map(CppPlatform::getOperatingSystemFamily).collect(Collectors.toSet()));
        String architectureSuffix = Dimensions.createDimensionSuffix(getBinary().getTargetPlatform().getArchitecture(), component.getBinaries().get().stream().map(CppBinary::getTargetPlatform).map(CppPlatform::getArchitecture).collect(Collectors.toSet()));

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
        return visualStudioVersion;
    }

    @Override
    public VersionNumber getSdkVersion() {
        return windowsSdkVersion;
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
        return ImmutableFileCollection.of();
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
