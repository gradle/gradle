/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.language.base.internal.compile.*;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

/**
 */
public class AbstractPlatformToolProvider implements PlatformToolProvider {
    protected final OperatingSystemInternal targetOperatingSystem;
    protected final ExecActionFactory execActionFactory;
    protected final boolean useCommandFile;
    protected final String outputFileSuffix;

    public AbstractPlatformToolProvider(OperatingSystemInternal targetOperatingSystem, boolean useCommandFile, ExecActionFactory execActionFactory) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.useCommandFile = useCommandFile;
        this.execActionFactory = execActionFactory;
        this.outputFileSuffix = "." + getObjectFileExtension();
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public String getObjectFileExtension() {
        return targetOperatingSystem.isWindows() ? "obj" : "o";
    }

    public String getExecutableName(String executablePath) {
        return targetOperatingSystem.getInternalOs().getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getSharedLibraryLinkFileName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getStaticLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getStaticLibraryName(libraryPath);
    }

    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(T spec) {
        if (spec instanceof CppCompileSpec) {
            return castCompiler(createCppCompiler());
        }
        if (spec instanceof CCompileSpec) {
            return castCompiler(createCCompiler());
        }
        if (spec instanceof ObjectiveCppCompileSpec) {
            return castCompiler(createObjectiveCppCompiler());
        }
        if (spec instanceof ObjectiveCCompileSpec) {
            return castCompiler(createObjectiveCCompiler());
        }
        if (spec instanceof WindowsResourceCompileSpec) {
            return castCompiler(createWindowsResourceCompiler());
        }
        if (spec instanceof AssembleSpec) {
            return castCompiler(createAssembler());
        }
        if (spec instanceof LinkerSpec) {
            return castCompiler(createLinker());
        }
        if (spec instanceof StaticLibraryArchiverSpec) {
            return castCompiler(createStaticLibraryArchiver());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    protected final void unavailableTool(String message) {
        throw new RuntimeException(message);
    }

    protected Compiler<?> createCppCompiler() {
        unavailableTool("C++ compiler is not available");
        return null;
    }

    protected Compiler<?> createCCompiler() {
        unavailableTool("C compiler is not available");
        return null;
    }

    protected Compiler<?> createObjectiveCppCompiler() {
        unavailableTool("Obj-C++ compiler is not available");
        return null;
    }

    protected Compiler<?> createObjectiveCCompiler() {
        unavailableTool("Obj-C compiler is not available");
        return null;
    }

    protected Compiler<?> createWindowsResourceCompiler() {
        unavailableTool("Windows resource compiler is not available");
        return null;
    }

    protected Compiler<?> createAssembler() {
        unavailableTool("Assembler is not available");
        return null;
    }

    protected Compiler<?> createLinker() {
        unavailableTool("Linker is not available");
        return null;
    }

    protected Compiler<?> createStaticLibraryArchiver() {
        unavailableTool("Static library archiver is not available");
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends CompileSpec> Compiler<T> castCompiler(Compiler<?> compiler) {
        return (Compiler<T>) compiler;
    }
}
