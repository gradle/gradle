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
import org.gradle.plugins.binaries.model.BinaryCompileSpec;
import org.gradle.plugins.binaries.model.ToolChain;
import org.gradle.plugins.binaries.model.ToolChainAdapter;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

public class VisualCppToolChainAdapter implements ToolChainAdapter {

    public static final String NAME = "visualCpp";
    static final String EXECUTABLE = "cl.exe";

    private final File executable;
    private final Factory<ExecAction> execActionFactory;
    private final OperatingSystem operatingSystem;

    public VisualCppToolChainAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem.findInPath(EXECUTABLE), operatingSystem, execActionFactory);
    }

    protected VisualCppToolChainAdapter(File executable, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this.executable = executable;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return String.format("Visual C++ (%s)", operatingSystem.getExecutableName(EXECUTABLE));
    }

    public boolean isAvailable() {
        return operatingSystem.isWindows() && executable != null && executable.exists();
    }

    public ToolChain create() {
        return new ToolChain() {
            public <T extends BinaryCompileSpec> Compiler<T> createCompiler(Class<T> specType) {
                if (!specType.isAssignableFrom(CppCompileSpec.class)) {
                    // TODO:DAZ Should introduce language instead of relying on spec here
                    throw new IllegalArgumentException(String.format("No suitable compiler available for %s.", specType));
                }
                return (Compiler<T>) new VisualCppCompiler(executable, execActionFactory);
            }
        };
    }
}
