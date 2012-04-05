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

package org.gradle.plugins.cpp.compiler.internal;

import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.internal.CompilerAdapter;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;

import java.io.File;

abstract public class CommandLineCppCompilerAdapter<T extends CppCompileSpec> implements CompilerAdapter<T> {

    private final File executable;
    private final Factory<ExecAction> execActionFactory;
    private OperatingSystem operatingSystem;

    protected CommandLineCppCompilerAdapter(String executableName, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem.findInPath(executableName), operatingSystem, execActionFactory);
    }

    protected CommandLineCppCompilerAdapter(File executable, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this.executable = executable;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
    }

    protected File getExecutable() {
        return executable;
    }

    protected Factory<ExecAction> getExecActionFactory() {
        return execActionFactory;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public boolean isAvailable() {
        return executable != null && executable.exists();
    }

}
