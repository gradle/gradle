/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal

import org.gradle.api.plugins.ExtensionAware
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.language.PreprocessingTool
import org.gradle.nativebinaries.toolchain.internal.MacroArgsConverter

class VisualStudioProjectConfiguration {
    private final DefaultVisualStudioProject vsProject
    private final String configurationName
    private final String platformName
    final ProjectNativeBinaryInternal binary
    final String type = "Makefile"

    VisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, String platformName, ProjectNativeBinary binary) {
        this.vsProject = vsProject
        this.configurationName = configurationName
        this.platformName = platformName
        this.binary = binary as ProjectNativeBinaryInternal
    }

    String getName() {
        return "${configurationName}|${platformName}"
    }

    String getConfigurationName() {
        return configurationName
    }

    String getPlatformName() {
        return platformName
    }

    String getBuildTask() {
        return taskPath(binary.name)
    }

    String getCleanTask() {
        return taskPath("clean")
    }

    // TODO:DAZ Make it easier to find the lifecycle task and clean task for a binary and use task.path.
    private String taskPath(String taskName) {
        String projectPath = binary.component.projectPath
        if (projectPath == ":") {
            return ":${taskName}"
        }
        return "${projectPath}:${taskName}"
    }

    File getOutputFile() {
        return binary.primaryOutput
    }

    boolean isDebug() {
        return binary.buildType.name != 'release'
    }

    List<String> getCompilerDefines() {
        List<String> defines = []
        defines.addAll getDefines('cCompiler')
        defines.addAll getDefines('cppCompiler')
        defines.addAll getDefines('rcCompiler')
        return defines
    }

    private List<String> getDefines(String tool) {
        PreprocessingTool rcCompiler = findCompiler(tool)
        return rcCompiler == null ? [] : new MacroArgsConverter().transform(rcCompiler.macros)
    }

    private PreprocessingTool findCompiler(String tool) {
        ExtensionAware extendedBinary = binary as ExtensionAware;
        return extendedBinary.extensions.findByName(tool) as PreprocessingTool
    }

    List<File> getIncludePaths() {
        def includes = [] as Set
        binary.source.withType(HeaderExportingSourceSet).each { HeaderExportingSourceSet sourceSet ->
            includes.addAll sourceSet.exportedHeaders.srcDirs
        }
        binary.libs*.includeRoots.each {
            includes.addAll it.files
        }
        return includes as List
    }

    DefaultVisualStudioProject getProject() {
        return vsProject
    }
}
