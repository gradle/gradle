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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.Tool;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.AbstractToolChain;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.process.internal.ExecActionFactory;

/**
 * A tool chain that has GCC semantics, where all platform variants are produced by varying the tool args.
 */
public abstract class GccCompatibleToolChain extends AbstractToolChain implements PlatformToolChain {
    private final ExecActionFactory execActionFactory;
    protected final ToolRegistry tools;

    public GccCompatibleToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolRegistry tools) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.tools = tools;
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        for (ToolType key : ToolType.values()) {
            availability.mustExist(key.getToolName(), tools.locate(key));
        }
    }

    public PlatformToolChain target(Platform targetPlatform) {
        checkAvailable();
        checkPlatform(targetPlatform);
        return this;
    }

    private void checkPlatform(Platform targetPlatform) {
        if (!targetPlatform.getOperatingSystem().isCurrent()) {
            throw new IllegalStateException(String.format("Tool chain %s cannot build for os: %s", getName(), targetPlatform.getOperatingSystem().getName()));
        }
        ArchitectureInternal arch = (ArchitectureInternal) targetPlatform.getArchitecture();
        if (arch != ArchitectureInternal.TOOL_CHAIN_DEFAULT && !arch.isI386() && !arch.isAmd64()) {
            throw new IllegalStateException(String.format("Tool chain %s cannot build for architecture: %s", getName(), targetPlatform.getArchitecture().getName()));
        }
    }

    protected boolean canUseCommandFile() {
        return true;
    }

    public <T extends BinaryToolSpec> org.gradle.api.internal.tasks.compile.Compiler<T> createCppCompiler() {
        CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER);
        return (org.gradle.api.internal.tasks.compile.Compiler<T>) new CppCompiler(commandLineTool, canUseCommandFile());
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER);
        return (Compiler<T>) new CCompiler(commandLineTool, canUseCommandFile());
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER);
        return (Compiler<T>) new Assembler(commandLineTool);
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER);
        return (Compiler<T>) new GccLinker(commandLineTool, canUseCommandFile());
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER);
        return (Compiler<T>) new ArStaticLibraryArchiver(commandLineTool);
    }

    private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key) {
        CommandLineTool<T> commandLineTool = new CommandLineTool<T>(key.getToolName(), tools.locate(key), execActionFactory);
        // MinGW requires the path to be set
        commandLineTool.withPath(tools.getPath());
        return commandLineTool;
    }

    // TODO:DAZ Make this a composite where actions are user-extensible
    public void targetNativeBinaryForPlatform(NativeBinaryInternal nativeBinary) {
        org.gradle.nativebinaries.OperatingSystem targetOs = nativeBinary.getTargetPlatform().getOperatingSystem();
        ArchitectureInternal targetArch = (ArchitectureInternal) nativeBinary.getTargetPlatform().getArchitecture();

        // Can only build for currentOS
        if (!targetOs.isCurrent()) {
            nativeBinary.setBuildable(false);
            return;
        }

        // Allow the tool chain defaults to apply
        if (targetArch == ArchitectureInternal.TOOL_CHAIN_DEFAULT) {
            return;
        }
        // Target for i386 on current os
        if (targetArch.isI386()) {
            nativeBinary.getLinker().args("-m32");
            toolArgs(nativeBinary, "cCompiler", "-m32");
            toolArgs(nativeBinary, "cppCompiler", "-m32");
            if (targetOs.isMacOsX()) {
                toolArgs(nativeBinary, "assembler", "-arch", "i386");
            } else {
                toolArgs(nativeBinary, "assembler", "--32");
            }
            return;
        }
        // Target for amd64 on current os
        if (targetArch.isAmd64()) {
            nativeBinary.getLinker().args("-m64");
            toolArgs(nativeBinary, "cCompiler", "-m64");
            toolArgs(nativeBinary, "cppCompiler", "-m64");
            if (targetOs.isMacOsX()) {
                toolArgs(nativeBinary, "assembler", "-arch", "x86_64");
            } else {
                toolArgs(nativeBinary, "assembler", "--64");
            }
            return;
        }

        // Cannot build for any other architectures
        nativeBinary.setBuildable(false);
    }

    private void toolArgs(NativeBinary nativeBinary, String toolName, String... args) {
        ExtensionContainer extensions = ((ExtensionAware) nativeBinary).getExtensions();
        Tool tool = (Tool) extensions.findByName(toolName);
        if (tool != null) {
            tool.args(args);
        }
    }

}
