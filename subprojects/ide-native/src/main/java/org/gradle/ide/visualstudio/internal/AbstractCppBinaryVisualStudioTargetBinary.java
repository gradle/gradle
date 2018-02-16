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
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter;

import java.io.File;
import java.util.List;
import java.util.Set;

abstract public class AbstractCppBinaryVisualStudioTargetBinary implements VisualStudioTargetBinary {

    protected final Project project;
    protected final CppComponent component;

    public AbstractCppBinaryVisualStudioTargetBinary(Project project, CppComponent component) {
        this.project = project;
        this.component = component;
    }

    abstract CppBinary getBinary();

    @Override
    public String getProjectPath() {
        return project.getPath();
    }

    @Override
    public String getComponentName() {
        return project.getName();
    }

    @Override
    public String getProjectName() {
        return project.getName() + getProjectType().getSuffix();
    }

    @Override
    public String getConfigurationName() {
        // TODO: this is terrible
        if (getBinary().isOptimized()) {
            return "release";
        } else {
            return "debug";
        }

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
        return project.files();
    }

    @Override
    public FileCollection getHeaderFiles() {
        return component.getHeaderFiles();
    }

    @Override
    public String getCleanTaskPath() {
        return project.getPath() + ":clean";
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
