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

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerAdapter;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;

public class VisualCppCompilerAdapter extends CommandLineCppCompilerAdapter<CppCompileSpec> {

    static final String EXECUTABLE = "cl.exe";

    public VisualCppCompilerAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        super(EXECUTABLE, operatingSystem, execActionFactory);
    }

    public String getName() {
        return "visualCpp";
    }

    @Override
    public String toString() {
        return String.format("Visual C++ (%s)", getOperatingSystem().getExecutableName(EXECUTABLE));
    }

    public boolean isAvailable() {
        return getOperatingSystem().isWindows() && super.isAvailable();
    }

    public Compiler<CppCompileSpec> createCompiler() {
        return new VisualCppCompiler(getExecutable(), getExecActionFactory());
    }
}
