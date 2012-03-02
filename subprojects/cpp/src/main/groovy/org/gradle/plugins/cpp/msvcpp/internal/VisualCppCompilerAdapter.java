/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.cpp.msvcpp.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.internal.CompilerAdapter;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;

public class VisualCppCompilerAdapter implements CompilerAdapter<GppCompileSpec> {
    private final VisualCppCompiler compiler;

    public VisualCppCompilerAdapter(ProjectInternal project) {
        compiler = new VisualCppCompiler(project.getFileResolver());
    }

    public String getName() {
        return "visualCpp";
    }

    @Override
    public String toString() {
        return String.format("Visual C++ (%s)", OperatingSystem.current().getExecutableName(VisualCppCompiler.EXECUTABLE));
    }

    public boolean isAvailable() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        return operatingSystem.isWindows() && operatingSystem.findInPath(VisualCppCompiler.EXECUTABLE) != null;
    }

    public Compiler<GppCompileSpec> createCompiler(Binary binary) {
        return compiler;
    }
}
