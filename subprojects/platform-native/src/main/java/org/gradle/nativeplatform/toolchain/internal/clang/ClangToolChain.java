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

package org.gradle.nativeplatform.toolchain.internal.clang;

import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.gcc.AbstractGccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.gcc.TargetPlatformConfiguration;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.process.internal.ExecActionFactory;

import java.util.List;

@NonNullApi
public class ClangToolChain extends AbstractGccCompatibleToolChain implements Clang {

    public static final String DEFAULT_NAME = "clang";

    public ClangToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProviderFactory metaDataProviderFactory, SystemLibraryDiscovery standardLibraryDiscovery, Instantiator instantiator, WorkerLeaseService workerLeaseService, SystemInfo systemInfo) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory.clang(), standardLibraryDiscovery, instantiator, workerLeaseService, systemInfo);
        target(new Intel32Architecture());
        target(new Intel64Architecture());
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

    @Override
    protected String getTypeName() {
        return "Clang";
    }


    @Override
    protected void addDefaultTools(NativePlatformInternal targetPlatform, DefaultGccPlatformToolChain toolChain) {
        addNativeTools(toolChain);
    }

    private void addNativeTools(DefaultGccPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, "clang"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, "clang++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, "clang++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, "ar"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVECPP_COMPILER, "clang++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVEC_COMPILER, "clang"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, "clang"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.SYMBOL_EXTRACTOR, SymbolExtractorOsConfig.current().getExecutableName()));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STRIPPER, "strip"));
    }
}
