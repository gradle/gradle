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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.PlatformToolChain;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.process.internal.ExecActionFactory;

import java.util.Arrays;
import java.util.List;

public class GnuCompatibleToolChain implements PlatformToolChain {
    private final ToolRegistry tools;
    private final OperatingSystem operatingSystem;
    private final ExecActionFactory execActionFactory;
    private final Platform targetPlatform;
    private final boolean useCommandFile;

    public GnuCompatibleToolChain(ToolRegistry tools, OperatingSystem operatingSystem, ExecActionFactory execActionFactory,
                                  Platform targetPlatform, boolean useCommandFile) {
        this.tools = tools;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
        this.targetPlatform = targetPlatform;
        this.useCommandFile = useCommandFile;
    }

    public <T extends BinaryToolSpec> org.gradle.api.internal.tasks.compile.Compiler<T> createCppCompiler() {
        CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER);
        return (Compiler<T>) new CppCompiler(commandLineTool, useCommandFile);
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER);
        return (Compiler<T>) new CCompiler(commandLineTool, useCommandFile);
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER);
        return (Compiler<T>) new Assembler(commandLineTool);
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER);
        return (Compiler<T>) new GccLinker(commandLineTool, useCommandFile);
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER);
        return (Compiler<T>) new ArStaticLibraryArchiver(commandLineTool);
    }

    private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key) {
        CommandLineTool<T> commandLineTool = new CommandLineTool<T>(key.getToolName(), tools.locate(key), execActionFactory);
        commandLineTool.withPath(tools.getPath());
        targetToPlatform(commandLineTool, key);
        return commandLineTool;
    }

    private void targetToPlatform(CommandLineTool tool, ToolType key) {
        switch (key) {
            case CPP_COMPILER:
            case C_COMPILER:
            case LINKER:
                tool.withArguments(gccSwitches());
                return;
            case ASSEMBLER:
                tool.withArguments(asSwitches());
                return;
            case STATIC_LIB_ARCHIVER:
        }
    }

    private List<String> gccSwitches() {
        switch (targetPlatform.getArchitecture()) {
            case I386:
                return args("-m32");
            case AMD64:
                return args("-m64");
            default:
                return args();
        }
    }

    private List<String> asSwitches() {
        boolean osx = operatingSystem.isMacOsX();
        switch (targetPlatform.getArchitecture()) {
            case I386:
                return osx ? args("-arch", "i386") : args("--32");
            case AMD64:
                return osx ? args("-arch", "x86_64") : args("--64");
            default:
                return args();
        }
    }

    private List<String> args(String... values) {
        return Arrays.asList(values);
    }
}
