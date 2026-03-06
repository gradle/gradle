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

package org.gradle.language.cpp.internal.tooling;

import org.gradle.plugins.ide.internal.tooling.model.LaunchableGradleTask;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class DefaultCompilationDetails implements Serializable {
    private final LaunchableGradleTask compileTask;
    private final File compilerExe;
    private final File workingDir;
    private final List<DefaultSourceFile> sources;
    private final List<File> headerDirs;
    private final List<File> systemHeaderDirs;
    private final List<File> userHeaderDirs;
    private final List<DefaultMacroDirective> macroDefines;
    private final List<String> additionalArgs;

    public DefaultCompilationDetails(LaunchableGradleTask compileTask, File compilerExe, File workingDir, List<DefaultSourceFile> sources, List<File> headerDirs, List<File> systemHeaderDirs, List<File> userHeaderDirs, List<DefaultMacroDirective> macroDefines, List<String> additionalArgs) {
        this.compileTask = compileTask;
        this.compilerExe = compilerExe;
        this.workingDir = workingDir;
        this.sources = sources;
        this.headerDirs = headerDirs;
        this.systemHeaderDirs = systemHeaderDirs;
        this.userHeaderDirs = userHeaderDirs;
        this.macroDefines = macroDefines;
        this.additionalArgs = additionalArgs;
    }

    public LaunchableGradleTask getCompileTask() {
        return compileTask;
    }

    public File getCompilerExecutable() {
        return compilerExe;
    }

    public File getCompileWorkingDir() {
        return workingDir;
    }

    public List<DefaultSourceFile> getSources() {
        return sources;
    }

    public List<File> getHeaderDirs() {
        return headerDirs;
    }

    public List<File> getFrameworkSearchPaths() {
        return Collections.emptyList();
    }

    public List<File> getSystemHeaderSearchPaths() {
        return systemHeaderDirs;
    }

    public List<File> getUserHeaderSearchPaths() {
        return userHeaderDirs;
    }

    public List<DefaultMacroDirective> getMacroDefines() {
        return macroDefines;
    }

    public List<String> getMacroUndefines() {
        return Collections.emptyList();
    }

    public List<String> getAdditionalArgs() {
        return additionalArgs;
    }
}
