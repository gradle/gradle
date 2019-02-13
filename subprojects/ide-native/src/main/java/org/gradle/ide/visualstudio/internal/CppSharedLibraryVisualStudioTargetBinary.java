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

import org.gradle.api.file.ProjectLayout;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppSharedLibrary;

import java.io.File;

public class CppSharedLibraryVisualStudioTargetBinary extends AbstractCppBinaryVisualStudioTargetBinary {
    private final CppSharedLibrary binary;

    public CppSharedLibraryVisualStudioTargetBinary(String projectName, String projectPath, CppComponent component, CppSharedLibrary binary, ProjectLayout projectLayout) {
        super(projectName, projectPath, component, projectLayout);
        this.binary = binary;
    }

    @Override
    CppBinary getBinary() {
        return binary;
    }

    @Override
    public ProjectType getProjectType() {
        return ProjectType.DLL;
    }

    @Override
    public boolean isExecutable() {
        return false;
    }

    @Override
    public String getBuildTaskPath() {
        return binary.getLinkTask().get().getPath();
    }

    @Override
    public File getOutputFile() {
        return binary.getRuntimeFile().get().getAsFile();
    }
}
