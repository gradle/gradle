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

package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.AbstractToolChain;
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;

public class VisualCppToolChain extends AbstractToolChain implements VisualCpp {

    public static final String DEFAULT_NAME = "visualCpp";

    private final ExecActionFactory execActionFactory;
    private final VisualStudioLocator visualStudioLocator;
    private File installDir;

    public VisualCppToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory,
                              VisualStudioLocator visualStudioLocator) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.visualStudioLocator = visualStudioLocator;
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
        checkFound("Visual Studio installation", locateVisualStudio(), availability);
        checkFound("Windows SDK", locateWindowsSdk(), availability);
    }

    private void checkFound(String name, VisualStudioLocator.SearchResult visualStudio, ToolChainAvailability availability) {
        if (!visualStudio.isFound()) {
            availability.unavailable(String.format("%s cannot be located. Searched in %s.", name, visualStudio.getSearchLocations()));
        }
    }

    private VisualStudioLocator.SearchResult locateVisualStudio() {
        if (installDir != null) {
            return visualStudioLocator.locateVisualStudio(installDir);
        }
        return visualStudioLocator.locateDefaultVisualStudio();
    }

    private VisualStudioLocator.SearchResult locateWindowsSdk() {
        return visualStudioLocator.locateDefaultWindowsSdk();
    }

    public File getInstallDir() {
        return installDir;
    }

    public void setInstallDir(Object installDirPath) {
        this.installDir = resolve(installDirPath);
    }

    public PlatformToolChain target(Platform targetPlatform) {
        checkAvailable();
        checkPlatform(targetPlatform);

        VisualStudioInstall visualStudioInstall = new VisualStudioInstall(locateVisualStudio().getResult());
        WindowsSdk windowsSdk = new WindowsSdk(locateWindowsSdk().getResult());
        return new VisualCppPlatformToolChain(visualStudioInstall, windowsSdk, targetPlatform);
    }

    private void checkPlatform(Platform targetPlatform) {
        ArchitectureInternal targetArch = (ArchitectureInternal) targetPlatform.getArchitecture();
        org.gradle.nativebinaries.OperatingSystem targetOs = targetPlatform.getOperatingSystem();

        if (!targetOs.isWindows() || !isSupportedArchitecture(targetArch)) {
            throw new IllegalStateException(String.format("Tool chain %s cannot build for platform: %s", getName(), targetPlatform.getName()));
        }
    }

    public boolean canTargetPlatform(Platform targetPlatform) {
        org.gradle.nativebinaries.OperatingSystem targetOs = targetPlatform.getOperatingSystem();
        ArchitectureInternal targetArch = (ArchitectureInternal) targetPlatform.getArchitecture();

        return targetOs.isWindows() && isSupportedArchitecture(targetArch);
    }

    private boolean isSupportedArchitecture(ArchitectureInternal targetArch) {
        return targetArch == ArchitectureInternal.TOOL_CHAIN_DEFAULT
                || targetArch.isI386()
                || targetArch.isAmd64()
                || targetArch.isIa64();
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    private class VisualCppPlatformToolChain implements PlatformToolChain {
        private final VisualStudioInstall install;
        private final WindowsSdk sdk;
        private final Platform targetPlatform;

        private VisualCppPlatformToolChain(VisualStudioInstall install, WindowsSdk sdk, Platform targetPlatform) {
            this.install = install;
            this.sdk = sdk;
            this.targetPlatform = targetPlatform;
        }

        public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
            CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool(ToolType.CPP_COMPILER, install.getCompiler(targetPlatform));
            Transformer<CppCompileSpec, CppCompileSpec> specTransformer = addIncludePath();
            commandLineTool.withSpecTransformer(specTransformer);
            return (Compiler<T>) new CppCompiler(commandLineTool);
        }

        public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
            CommandLineTool<CCompileSpec> commandLineTool = commandLineTool(ToolType.C_COMPILER, install.getCompiler(targetPlatform));
            Transformer<CCompileSpec, CCompileSpec> specTransformer = addIncludePath();
            commandLineTool.withSpecTransformer(specTransformer);
            return (Compiler<T>) new CCompiler(commandLineTool);
        }

        public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
            CommandLineTool<AssembleSpec> commandLineTool = commandLineTool(ToolType.ASSEMBLER, install.getAssembler(targetPlatform));
            return (Compiler<T>) new Assembler(commandLineTool);
        }

        public <T extends LinkerSpec> Compiler<T> createLinker() {
            CommandLineTool<LinkerSpec> commandLineTool = commandLineTool(ToolType.LINKER, install.getLinker(targetPlatform));
            commandLineTool.withSpecTransformer(addLibraryPath());
            return (Compiler<T>) new LinkExeLinker(commandLineTool);
        }

        public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
            CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool(ToolType.STATIC_LIB_ARCHIVER, install.getStaticLibArchiver(targetPlatform));
            return (Compiler<T>) new LibExeStaticLibraryArchiver(commandLineTool);
        }

        private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(ToolType key, File exe) {
            CommandLineTool<T> tool = new CommandLineTool<T>(key.getToolName(), exe, execActionFactory);

            // The visual C++ tools use the path to find other executables
            tool.withPath(install.getVisualCppBin(targetPlatform), sdk.getBinDir(), install.getCommonIdeBin());

            return tool;
        }

        public String getOutputType() {
            return String.format("%s-%s", getName(), operatingSystem.getName());
        }

        private <T extends NativeCompileSpec> Transformer<T, T> addIncludePath() {
            return new Transformer<T, T>() {
                public T transform(T original) {
                    original.include(install.getVisualCppInclude());
                    return original;
                }
            };
        }

        private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
            return new Transformer<LinkerSpec, LinkerSpec>() {
                public LinkerSpec transform(LinkerSpec original) {
                    original.libraryPath(install.getVisualCppLib(targetPlatform), sdk.getLibDir(targetPlatform));
                    return original;
                }
            };
        }
    }
}
