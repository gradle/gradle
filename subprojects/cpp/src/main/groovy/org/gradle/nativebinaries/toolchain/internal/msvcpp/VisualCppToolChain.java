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
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec;
import org.gradle.nativebinaries.language.c.internal.CCompileSpec;
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec;
import org.gradle.nativebinaries.language.objectivec.internal.ObjectiveCCompileSpec;
import org.gradle.nativebinaries.language.objectivecpp.internal.ObjectiveCppCompileSpec;
import org.gradle.nativebinaries.language.rc.internal.WindowsResourceCompileSpec;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.*;
import org.gradle.nativebinaries.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativebinaries.toolchain.internal.tools.DefaultCommandLineToolConfiguration;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

public class VisualCppToolChain extends ExtendableToolChain implements VisualCpp, ToolChainInternal {

    private final String name;
    protected final OperatingSystem operatingSystem;
    private final FileResolver fileResolver;

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
                              VisualStudioLocator visualStudioLocator, WindowsSdkLocator windowsSdkLocator, Instantiator instantiator) {
        super(CommandLineToolConfigurationInternal.class, name, operatingSystem, fileResolver, instantiator);

        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "cCompiler"));
        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "cppCompiler"));
        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "linker"));
        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "staticLibArchiver"));
        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "assembler"));
        add(instantiator.newInstance(DefaultCommandLineToolConfiguration.class, "rcCompiler"));

        this.name = name;
        this.operatingSystem = operatingSystem;
        this.fileResolver = fileResolver;
        this.execActionFactory = execActionFactory;
        this.visualStudioLocator = visualStudioLocator;
        this.windowsSdkLocator = windowsSdkLocator;
    }

    protected String getTypeName() {
        return "Visual Studio";
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

    public PlatformToolChain select(Platform targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (visualCpp != null && !visualCpp.isSupportedPlatform(targetPlatform)) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolChain(result);
        }
        return new VisualCppPlatformToolChain(getAsMap(), visualCpp, windowsSdk, targetPlatform);
    }

    private ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
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

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return String.format("Tool chain '%s' (%s)", getName(), getTypeName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getOutputType() {
        return String.format("%s-%s", getName(), operatingSystem.getName());
    }

    public String getExecutableName(String executablePath) {
        return operatingSystem.getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryName) {
        return operatingSystem.getSharedLibraryName(libraryName);
    }

    public String getStaticLibraryName(String libraryName) {
        return operatingSystem.getStaticLibraryName(libraryName);
    }

    protected File resolve(Object path) {
        return fileResolver.resolve(path);
    }

    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    private class VisualCppPlatformToolChain implements PlatformToolChain {
        private SortedMap<String, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
        private final VisualCppInstall visualCpp;
        private final WindowsSdk sdk;
        private final Platform targetPlatform;

        private VisualCppPlatformToolChain(SortedMap<String, CommandLineToolConfigurationInternal> commandLineToolConfigurations, VisualCppInstall visualCpp, WindowsSdk sdk, Platform targetPlatform) {
            this.commandLineToolConfigurations = commandLineToolConfigurations;
            this.visualCpp = visualCpp;
            this.sdk = sdk;
            this.targetPlatform = targetPlatform;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }

        public <T extends CompileSpec> Compiler<T> newCompiler(T spec) {
            if (spec instanceof CppCompileSpec) {
                return (Compiler) createCppCompiler();
            }
            if (spec instanceof CCompileSpec) {
                return (Compiler) createCCompiler();
            }
            if (spec instanceof ObjectiveCppCompileSpec) {
                throw new RuntimeException("Objective-C++ is not available on the Visual C++ toolchain");
            }
            if (spec instanceof ObjectiveCCompileSpec) {
                throw new RuntimeException("Objective-C is not available on the Visual C++ toolchain");
            }
            if (spec instanceof WindowsResourceCompileSpec) {
                return (Compiler) createWindowsResourceCompiler();
            }
            if (spec instanceof AssembleSpec) {
                return (Compiler) createAssembler();
            }
            if (spec instanceof LinkerSpec) {
                return (Compiler) createLinker();
            }
            if (spec instanceof StaticLibraryArchiverSpec) {
                return (Compiler) createStaticLibraryArchiver();
            }
            throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
        }

        public Compiler<CppCompileSpec> createCppCompiler() {
            CommandLineTool commandLineTool = tool("C++ compiler", visualCpp.getCompiler(targetPlatform));
            CppCompiler cppCompiler = new CppCompiler(commandLineTool, invocation(commandLineToolConfigurations.get("cppCompiler")), addIncludePathAndDefinitions(CppCompileSpec.class));
            return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, ".obj");
        }

        public Compiler<CCompileSpec> createCCompiler() {
            CommandLineTool commandLineTool = tool("C compiler", visualCpp.getCompiler(targetPlatform));
            CCompiler cCompiler = new CCompiler(commandLineTool, invocation(commandLineToolConfigurations.get("cCompiler")), addIncludePathAndDefinitions(CCompileSpec.class));
            return new OutputCleaningCompiler<CCompileSpec>(cCompiler, ".obj");
        }

        public Compiler<AssembleSpec> createAssembler() {
            CommandLineTool commandLineTool = tool("Assembler", visualCpp.getAssembler(targetPlatform));
            return new Assembler(commandLineTool, invocation(commandLineToolConfigurations.get("assembler")));
        }

        public Compiler<WindowsResourceCompileSpec> createWindowsResourceCompiler() {
            CommandLineTool commandLineTool = tool("Windows resource compiler", sdk.getResourceCompiler(targetPlatform));
            WindowsResourceCompiler windowsResourceCompiler = new WindowsResourceCompiler(commandLineTool, invocation(commandLineToolConfigurations.get("rcCompiler")), addIncludePathAndDefinitions(WindowsResourceCompileSpec.class));
            return new OutputCleaningCompiler<WindowsResourceCompileSpec>(windowsResourceCompiler, ".res");
        }

        public Compiler<LinkerSpec> createLinker() {
            CommandLineTool commandLineTool = tool("Linker", visualCpp.getLinker(targetPlatform));
            return new LinkExeLinker(commandLineTool, invocation(commandLineToolConfigurations.get("linker")), addLibraryPath());
        }

        public Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
            CommandLineTool commandLineTool = tool("Static library archiver", visualCpp.getArchiver(targetPlatform));
            return new LibExeStaticLibraryArchiver(commandLineTool, invocation(commandLineToolConfigurations.get("staticLibArchiver")));
        }

        private CommandLineTool tool(String toolName, File exe) {
            return new CommandLineTool(toolName, exe, execActionFactory);
        }

        private CommandLineToolInvocation invocation(CommandLineToolConfigurationInternal commandLineToolConfiguration) {
            MutableCommandLineToolInvocation invocation = new DefaultCommandLineToolInvocation();
            // The visual C++ tools use the path to find other executables
            // TODO:ADAM - restrict this to the specific path for the target tool
            invocation.addPath(visualCpp.getPath(targetPlatform));
            invocation.addPath(sdk.getBinDir(targetPlatform));
            // Clear environment variables that might effect cl.exe & link.exe
            clearEnvironmentVars(invocation, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

            invocation.addPostArgsAction(commandLineToolConfiguration.getArgAction());
            return invocation;
        }

        private void clearEnvironmentVars(MutableCommandLineToolInvocation invocation, String... names) {
            // TODO:DAZ This check should really be done in the compiler process
            Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
            for (String name : names) {
                Object value = environmentVariables.get(name);
                if (value != null) {
                    LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                    invocation.addEnvironmentVar(name, "");
                }
            }
        }

        public String getOutputType() {
            return String.format("%s-%s", getName(), operatingSystem.getName());
        }

        // TODO:DAZ These should be modelled properly, not hidden in a compile spec transformation
        private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions(Class<T> type) {
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
