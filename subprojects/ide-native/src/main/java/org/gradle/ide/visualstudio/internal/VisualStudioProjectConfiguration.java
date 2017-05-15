/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VisualStudioProjectConfiguration {
    private final DefaultVisualStudioProject vsProject;
    private final String configurationName;
    private final String platformName;
    private final NativeBinarySpecInternal binary;
    private final String type = "Makefile";

    public VisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, String platformName, NativeBinarySpec binary) {
        this.vsProject = vsProject;
        this.configurationName = configurationName;
        this.platformName = platformName;
        this.binary = (NativeBinarySpecInternal) binary;
    }

    public String getName() {
        return configurationName + "|" + platformName;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getBuildTask() {
        return binary.getTasks().getBuild().getPath();
    }

    public String getCleanTask() {
        return taskPath("clean");
    }

    private String taskPath(final String taskName) {
        final String projectPath = binary.getComponent().getProjectPath();
        if (":".equals(projectPath)) {
            return ":" + taskName;
        }

        return projectPath + ":" + taskName;
    }

    public File getOutputFile() {
        return binary.getPrimaryOutput();
    }

    public boolean isDebug() {
        return !"release".equals(binary.getBuildType().getName());
    }

    public List<String> getCompilerDefines() {
        List<String> defines = new ArrayList<String>();
        defines.addAll(getDefines("cCompiler"));
        defines.addAll(getDefines("cppCompiler"));
        defines.addAll(getDefines("rcCompiler"));
        return defines;
    }

    private List<String> getDefines(String tool) {
        PreprocessingTool rcCompiler = findCompiler(tool);
        return rcCompiler == null ? new ArrayList() : new MacroArgsConverter().transform(rcCompiler.getMacros());
    }

    private PreprocessingTool findCompiler(String tool) {
        return (PreprocessingTool) binary.getToolByName(tool);
    }

    public List<File> getIncludePaths() {
        Set<File> includes = new LinkedHashSet<File>();

        for (LanguageSourceSet sourceSet : binary.getInputs()) {
            if (sourceSet instanceof HeaderExportingSourceSet) {
                includes.addAll(((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSrcDirs());
            }
        }

        for (NativeDependencySet lib : binary.getLibs()) {
            includes.addAll(lib.getIncludeRoots().getFiles());
        }

        return new ArrayList<File>(includes);
    }

    public DefaultVisualStudioProject getProject() {
        return vsProject;
    }

    public final NativeBinarySpecInternal getBinary() {
        return binary;
    }

    public final String getType() {
        return type;
    }
}
