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

package org.gradle.nativecode.toolchain.internal.msvcpp;

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.base.internal.*;
import org.gradle.nativecode.language.asm.internal.AssembleSpec;
import org.gradle.nativecode.language.c.internal.CCompileSpec;
import org.gradle.nativecode.language.cpp.internal.CppCompileSpec;
import org.gradle.nativecode.toolchain.internal.CommandLineTool;
import org.gradle.nativecode.toolchain.internal.Tool;
import org.gradle.nativecode.toolchain.internal.ToolRegistry;
import org.gradle.process.internal.ExecAction;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class VisualCppToolChain extends AbstractToolChain {

    public static final String DEFAULT_NAME = "visualCpp";

    private final ToolRegistry tools;
    private final Factory<ExecAction> execActionFactory;
    private final Map<String, String> environment = new HashMap<String, String>();

    private File installDir;

    public VisualCppToolChain(String name, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        super(name, operatingSystem);
        this.tools = new ToolRegistry(operatingSystem);
        this.execActionFactory = execActionFactory;

        tools.setExeName(Tool.CPP_COMPILER, "cl.exe");
        tools.setExeName(Tool.C_COMPILER, "cl.exe");
        tools.setExeName(Tool.ASSEMBLER, "ml.exe");
        tools.setExeName(Tool.LINKER, "link.exe");
        tools.setExeName(Tool.STATIC_LIB_ARCHIVER, "lib.exe");
    }

    @Override
    protected String getTypeName() {
        return "Visual C++";
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        if (!operatingSystem.isWindows()) {
            availability.unavailable("Not available on this operating system.");
            return;
        }
        for (Tool key : Tool.values()) {
            availability.mustExist(key.getToolName(), tools.locate(key));
        }
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
        checkAvailable();
        CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(Tool.CPP_COMPILER);
        return (Compiler<T>) new CppCompiler(commandLineTool);
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        checkAvailable();
        CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(Tool.C_COMPILER);
        return (Compiler<T>) new CCompiler(commandLineTool);
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        checkAvailable();
        CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(Tool.ASSEMBLER);
        return (Compiler<T>) new Assembler(commandLineTool);
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        checkAvailable();
        CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(Tool.LINKER);
        return (Compiler<T>) new LinkExeLinker(commandLineTool);
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        checkAvailable();
        CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(Tool.STATIC_LIB_ARCHIVER);
        return (Compiler<T>) new LibExeStaticLibraryArchiver(commandLineTool);
    }

    private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(Tool key) {
        CommandLineTool<T> commandLineTool = new CommandLineTool<T>(key.getToolName(), tools.locate(key), execActionFactory);
        commandLineTool.withPath(tools.getPath());
        commandLineTool.withEnvironment(environment);
        return commandLineTool;
    }

    public File getInstallDir() {
        return installDir;
    }

    // TODO:DAZ Resolve object to file
    public void setInstallDir(File installDir) {
        this.installDir = installDir;

        VisualStudioInstall install = new VisualStudioInstall(installDir);
        tools.setPath(install.getPathEntries());
        environment.clear();
        environment.putAll(install.getEnvironment());
    }

    public String getCppCompiler() {
        return tools.getExeName(Tool.CPP_COMPILER);
    }

    public void setCppCompiler(String name) {
        tools.setExeName(Tool.CPP_COMPILER, name);
    }

    public String getCCompiler() {
        return tools.getExeName(Tool.C_COMPILER);
    }

    public void setCCompiler(String name) {
        tools.setExeName(Tool.C_COMPILER, name);
    }

    public String getAssembler() {
        return tools.getExeName(Tool.ASSEMBLER);
    }

    public void setAssembler(String name) {
        tools.setExeName(Tool.ASSEMBLER, name);
    }

    public String getLinker() {
        return tools.getExeName(Tool.LINKER);
    }

    public void setLinker(String name) {
        tools.setExeName(Tool.LINKER, name);
    }

    public String getStaticLibArchiver() {
        return tools.getExeName(Tool.STATIC_LIB_ARCHIVER);
    }

    public void setStaticLibArchiver(String name) {
        tools.setExeName(Tool.STATIC_LIB_ARCHIVER, name);
    }
}
