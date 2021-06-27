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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.process.internal.ExecActionFactory;

import java.util.Arrays;
import java.util.List;


/**
 * Compiler adapter for GCC.
 */
@NonNullApi
public class GccToolChain extends AbstractGccCompatibleToolChain implements Gcc {
    public static final String DEFAULT_NAME = "gcc";

    public GccToolChain(Instantiator instantiator, String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProviderFactory metaDataProviderFactory, SystemLibraryDiscovery standardLibraryDiscovery, WorkerLeaseService workerLeaseService, SystemInfo systemInfo) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory.gcc(), standardLibraryDiscovery, instantiator, workerLeaseService, systemInfo);
        target(new Intel32Architecture());
        target(new Intel64Architecture());
        target(new Ia64Architecture());
        target(new Arm32Architecture());
        target(new Arm64Architecture());
    }

    private static class Arm32Architecture implements TargetPlatformConfiguration {

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent() && targetPlatform.getArchitecture().isArm();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain platformToolChain) {
        }
    }

    private static class Arm64Architecture implements TargetPlatformConfiguration {

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent() && targetPlatform.getArchitecture().isArm64();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain platformToolChain) {
        }
    }

    private static class Intel32Architecture implements TargetPlatformConfiguration {

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent() && targetPlatform.getArchitecture().isI386();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain gccToolChain) {
            gccToolChain.compilerProbeArgs("-m32");
            Action<List<String>> m32args = new Action<List<String>>() {
                @Override
                public void execute(List<String> args) {
                    args.add("-m32");
                }
            };
            gccToolChain.getCppCompiler().withArguments(m32args);
            gccToolChain.getcCompiler().withArguments(m32args);
            gccToolChain.getObjcCompiler().withArguments(m32args);
            gccToolChain.getObjcppCompiler().withArguments(m32args);
            gccToolChain.getLinker().withArguments(m32args);
            gccToolChain.getAssembler().withArguments(m32args);

        }
    }

    private static class Intel64Architecture implements TargetPlatformConfiguration {
        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                && targetPlatform.getArchitecture().isAmd64();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain gccToolChain) {
            gccToolChain.compilerProbeArgs("-m64");
            Action<List<String>> m64args = new Action<List<String>>() {
                @Override
                public void execute(List<String> args) {
                    args.add("-m64");
                }
            };
            gccToolChain.getCppCompiler().withArguments(m64args);
            gccToolChain.getcCompiler().withArguments(m64args);
            gccToolChain.getObjcCompiler().withArguments(m64args);
            gccToolChain.getObjcppCompiler().withArguments(m64args);
            gccToolChain.getLinker().withArguments(m64args);
            gccToolChain.getAssembler().withArguments(m64args);
        }
    }

    private static class Ia64Architecture implements TargetPlatformConfiguration {

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                && targetPlatform.getArchitecture().isIa64();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain platformToolChain) {
        }
    }

    @Override
    protected String getTypeName() {
        return "GNU GCC";
    }

    @Override
    protected void initForImplementation(DefaultGccPlatformToolChain platformToolChain, GccMetadata versionResult) {
        platformToolChain.setCanUseCommandFile(versionResult.getVersion().getMajor() >= 4);
    }

    @Override
    protected void addDefaultTools(NativePlatformInternal targetPlatform, DefaultGccPlatformToolChain toolChain) {
        Architecture systemArchitecture = Architectures.forInput(systemInfo.getArchitectureName());
        Architecture targetArchitecture = targetPlatform.getArchitecture();

        List<Architecture> x86architectures = Arrays.asList(
            Architectures.of(Architectures.X86),
            Architectures.of(Architectures.X86_64)
        );

        if (systemArchitecture.equals(targetArchitecture) || // if compiling for the same platform
            (x86architectures.contains(targetArchitecture) && x86architectures.contains(systemArchitecture))) { // or native tools are compatible with that architecture
            addNativeTools(toolChain);
        } else {
            addCrossCompilingTools(targetPlatform, toolChain);
        }
    }

    private void addNativeTools(DefaultGccPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, "ar"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVECPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVEC_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.SYMBOL_EXTRACTOR, SymbolExtractorOsConfig.current().getExecutableName()));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STRIPPER, "strip"));
    }

    private void addCrossCompilingTools(NativePlatformInternal targetPlatform, DefaultGccPlatformToolChain toolChain) {
        if (OperatingSystem.current().isLinux()) {
            addLinuxCrossCompilingTools(targetPlatform, toolChain);
        }
    }

    private void addLinuxCrossCompilingTools(NativePlatformInternal targetPlatform, DefaultGccPlatformToolChain toolChain) {
        ArchitectureInternal targetArchitecture = targetPlatform.getArchitecture();
        if (targetArchitecture.isI386()) {
            addLinuxCrossCompilingVariantTools("i686", "", toolChain);
        } else if (targetArchitecture.isAmd64()) {
            addLinuxCrossCompilingVariantTools("x86_64", "", toolChain);
        } else if (targetArchitecture.isIa64()) {
            addLinuxCrossCompilingVariantTools("ia64", "", toolChain);
        } else if (targetArchitecture.isArm()) {
            addLinuxCrossCompilingVariantTools("arm", "eabihf", toolChain);
        } else if (targetArchitecture.isArm64()) {
            addLinuxCrossCompilingVariantTools("aarch64", "", toolChain);
        }
    }

    private void addLinuxCrossCompilingVariantTools(String prefix, String postfix, DefaultGccPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, prefix + "-linux-gnu" + postfix + "-gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, prefix + "-linux-gnu" + postfix + "-g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, prefix + "-linux-gnu" + postfix + "-g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, prefix + "-linux-gnu" + postfix + "-ar"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVECPP_COMPILER, prefix + "-linux-gnu" + postfix + "-g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVEC_COMPILER, prefix + "-linux-gnu" + postfix + "-gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, prefix + "-linux-gnu" + postfix + "-gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.SYMBOL_EXTRACTOR, prefix + "-linux-gnu" + postfix + "-objcopy"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STRIPPER, prefix + "-linux-gnu" + postfix + "-strip"));
    }
}
