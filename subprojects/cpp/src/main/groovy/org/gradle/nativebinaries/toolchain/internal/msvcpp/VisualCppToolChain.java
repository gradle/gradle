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
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.language.rc.internal.WindowsResourceCompileSpec;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.*;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

public class VisualCppToolChain extends AbstractToolChain implements VisualCpp {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisualCppToolChain.class);

    public static final String DEFAULT_NAME = "visualCpp";

    private final ExecActionFactory execActionFactory;
    private final VisualStudioLocator visualStudioLocator;
    private final WindowsSdkLocator windowsSdkLocator;
    private File installDir;
    private File windowsSdkDir;
    private VisualCppInstall visualCpp;
    private WindowsSdk windowsSdk;
    private ToolChainAvailability availability;

    public VisualCppToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory,
                              VisualStudioLocator visualStudioLocator, WindowsSdkLocator windowsSdkLocator) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.visualStudioLocator = visualStudioLocator;
        this.windowsSdkLocator = windowsSdkLocator;
    }

    @Override
    protected String getTypeName() {
        return "Visual Studio";
    }

    private void checkAvailable(ToolChainAvailability availability) {
        if (!operatingSystem.isWindows()) {
            availability.unavailable("Visual Studio is not available on this operating system.");
            return;
        }
        VisualStudioLocator.SearchResult visualStudioSearchResult = visualStudioLocator.locateVisualStudioInstalls(installDir);
        availability.mustBeAvailable(visualStudioSearchResult);
        if (visualStudioSearchResult.isAvailable()) {
            visualCpp = visualStudioSearchResult.getVisualStudio().getVisualCpp();
        }
        WindowsSdkLocator.SearchResult windowsSdkSearchResult = windowsSdkLocator.locateWindowsSdks(windowsSdkDir);
        availability.mustBeAvailable(windowsSdkSearchResult);
        if (windowsSdkSearchResult.isAvailable()) {
            windowsSdk = windowsSdkSearchResult.getSdk();
        }
    }

    public File getInstallDir() {
        return installDir;
    }

    public void setInstallDir(Object installDirPath) {
        this.installDir = resolve(installDirPath);
    }

    public File getWindowsSdkDir() {
        return windowsSdkDir;
    }

    public void setWindowsSdkDir(Object windowsSdkDirPath) {
        this.windowsSdkDir = resolve(windowsSdkDirPath);
    }

    private ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
    }

    public PlatformToolChain target(Platform targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (visualCpp != null && !visualCpp.isSupportedPlatform(targetPlatform)) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolChain(result);
        }
        return new VisualCppPlatformToolChain(visualCpp, windowsSdk, targetPlatform);
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    private class VisualCppPlatformToolChain implements PlatformToolChain {
        private final WindowsSdk sdk;
        private final Platform targetPlatform;
        private final VisualCppInstall visualCpp;

        private VisualCppPlatformToolChain(VisualCppInstall visualCpp, WindowsSdk sdk, Platform targetPlatform) {
            this.visualCpp = visualCpp;
            this.sdk = sdk;
            this.targetPlatform = targetPlatform;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }

        public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
            CommandLineTool<CppCompileSpec> commandLineTool = commandLineTool("C++ compiler", visualCpp.getCompiler(targetPlatform));
            Transformer<CppCompileSpec, CppCompileSpec> specTransformer = addIncludePathAndDefinitions();
            commandLineTool.withSpecTransformer(specTransformer);
            CppCompiler cppCompiler = new CppCompiler(commandLineTool);
            return (Compiler<T>) new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, ".obj");
        }

        public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
            CommandLineTool<CCompileSpec> commandLineTool = commandLineTool("C compiler", visualCpp.getCompiler(targetPlatform));
            Transformer<CCompileSpec, CCompileSpec> specTransformer = addIncludePathAndDefinitions();
            commandLineTool.withSpecTransformer(specTransformer);
            CCompiler cCompiler = new CCompiler(commandLineTool);
            return (Compiler<T>) new OutputCleaningCompiler<CCompileSpec>(cCompiler, ".obj");
        }

        public <T extends BinaryToolSpec> Compiler<T> createObjectiveCppCompiler() {
            throw new RuntimeException("Objective-C++ is not available on the Visual C++ toolchain");
        }

        public <T extends BinaryToolSpec> Compiler<T> createObjectiveCCompiler() {
            throw new RuntimeException("Objective-C is not available on the Visual C++ toolchain");
        }

        public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
            CommandLineTool<AssembleSpec> commandLineTool = commandLineTool("Assembler", visualCpp.getAssembler(targetPlatform));
            return (Compiler<T>) new Assembler(commandLineTool);
        }

        public <T extends BinaryToolSpec> Compiler<T> createWindowsResourceCompiler() {
            CommandLineTool<WindowsResourceCompileSpec> commandLineTool = commandLineTool("Windows resource compiler", sdk.getResourceCompiler(targetPlatform));
            Transformer<WindowsResourceCompileSpec, WindowsResourceCompileSpec> specTransformer = addIncludePathAndDefinitions();
            commandLineTool.withSpecTransformer(specTransformer);
            WindowsResourceCompiler windowsResourceCompiler = new WindowsResourceCompiler(commandLineTool);
            return (Compiler<T>) new OutputCleaningCompiler<WindowsResourceCompileSpec>(windowsResourceCompiler, ".res");
        }

        public <T extends LinkerSpec> Compiler<T> createLinker() {
            CommandLineTool<LinkerSpec> commandLineTool = commandLineTool("Linker", visualCpp.getLinker(targetPlatform));
            commandLineTool.withSpecTransformer(addLibraryPath());
            return (Compiler<T>) new LinkExeLinker(commandLineTool);
        }

        public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
            CommandLineTool<StaticLibraryArchiverSpec> commandLineTool = commandLineTool("Static library archiver", visualCpp.getArchiver(targetPlatform));
            return (Compiler<T>) new LibExeStaticLibraryArchiver(commandLineTool);
        }

        private <T extends BinaryToolSpec> CommandLineTool<T> commandLineTool(String toolName, File exe) {
            CommandLineTool<T> tool = new CommandLineTool<T>(toolName, exe, execActionFactory);

            // The visual C++ tools use the path to find other executables
            // TODO:ADAM - restrict this to the specific path for the target tool
            tool.withPath(visualCpp.getPath(targetPlatform));
            tool.withPath(sdk.getBinDir(targetPlatform));

            // Clear environment variables that might effect cl.exe & link.exe
            clearEnvironmentVars(tool, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");
            return tool;
        }

        private <T extends BinaryToolSpec> void clearEnvironmentVars(CommandLineTool<T> tool, String... names) {
            Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
            for (String name : names) {
                Object value = environmentVariables.get(name);
                if (value != null) {
                    LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                    tool.withEnvironmentVar(name, "");
                }
            }
        }

        public String getOutputType() {
            return String.format("%s-%s", getName(), operatingSystem.getName());
        }

        private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions() {
            return new Transformer<T, T>() {
                public T transform(T original) {
                    original.include(visualCpp.getIncludePath(targetPlatform));
                    original.include(sdk.getIncludeDirs());
                    for (Entry<String, String> definition : visualCpp.getDefinitions(targetPlatform).entrySet()) {
                        original.define(definition.getKey(), definition.getValue());
                    }
                    return original;
                }
            };
        }

        private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
            return new Transformer<LinkerSpec, LinkerSpec>() {
                public LinkerSpec transform(LinkerSpec original) {
                    original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform));
                    return original;
                }
            };
        }
    }
}
