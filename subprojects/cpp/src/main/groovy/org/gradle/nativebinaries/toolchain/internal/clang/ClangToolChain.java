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

package org.gradle.nativebinaries.toolchain.internal.clang;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.AbstractToolChain;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.*;
import org.gradle.process.internal.ExecActionFactory;

public class ClangToolChain extends AbstractToolChain {
    public static final String DEFAULT_NAME = "clang";
    private final ExecActionFactory execActionFactory;

    public ClangToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory) {
        super(name, operatingSystem, new ToolRegistry(operatingSystem), fileResolver);
        this.execActionFactory = execActionFactory;

        tools.setExeName(ToolType.CPP_COMPILER, "clang++");
        tools.setExeName(ToolType.C_COMPILER, "clang");
        tools.setExeName(ToolType.ASSEMBLER, "as");
        tools.setExeName(ToolType.LINKER, "clang++");
        tools.setExeName(ToolType.STATIC_LIB_ARCHIVER, "ar");
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        for (ToolType key : ToolType.values()) {
            availability.mustExist(key.getToolName(), tools.locate(key));
        }
    }

    @Override
    protected String getTypeName() {
        return "Clang";
    }

    public PlatformToolChain target(Platform targetPlatform) {
        checkAvailable();
        return new PlatformToolChain() {
            public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
                CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER);
                return (Compiler<T>) new CppCompiler(commandLineTool, true);
            }

            public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
                CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER);
                return (Compiler<T>) new CCompiler(commandLineTool, true);
            }

            public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
                CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER);
                return (Compiler<T>) new Assembler(commandLineTool);
            }

            public <T extends LinkerSpec> Compiler<T> createLinker() {
                CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER);
                return (Compiler<T>) new GccLinker(commandLineTool, true);
            }

            public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
                CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER);
                return (Compiler<T>) new ArStaticLibraryArchiver(commandLineTool);
            }

            private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key) {
                return new CommandLineTool<T>(key.getToolName(), tools.locate(key), execActionFactory);
            }
        };
    }
}
