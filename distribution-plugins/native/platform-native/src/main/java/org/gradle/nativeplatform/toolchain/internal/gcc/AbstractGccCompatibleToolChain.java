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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractorOsConfig;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * A tool chain that has GCC semantics.
 */
@NonNullApi
public abstract class AbstractGccCompatibleToolChain extends ExtendableToolChain<GccPlatformToolChain> implements GccCompatibleToolChain {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGccCompatibleToolChain.class);
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final ExecActionFactory execActionFactory;
    private final ToolSearchPath toolSearchPath;
    private final List<TargetPlatformConfiguration> platformConfigs = new ArrayList<TargetPlatformConfiguration>();
    private final Map<NativePlatform, PlatformToolProvider> toolProviders = Maps.newHashMap();
    private final CompilerMetaDataProvider<GccMetadata> metaDataProvider;
    private final SystemLibraryDiscovery standardLibraryDiscovery;
    private final Instantiator instantiator;
    private final WorkerLeaseService workerLeaseService;
    private int configInsertLocation;

    public AbstractGccCompatibleToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProvider<GccMetadata> metaDataProvider, SystemLibraryDiscovery standardLibraryDiscovery, Instantiator instantiator, WorkerLeaseService workerLeaseService) {
        this(name, buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, new ToolSearchPath(operatingSystem), metaDataProvider, standardLibraryDiscovery, instantiator, workerLeaseService);
    }

    AbstractGccCompatibleToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, ToolSearchPath tools, CompilerMetaDataProvider<GccMetadata> metaDataProvider, SystemLibraryDiscovery standardLibraryDiscovery, Instantiator instantiator, WorkerLeaseService workerLeaseService) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.toolSearchPath = tools;
        this.metaDataProvider = metaDataProvider;
        this.instantiator = instantiator;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.workerLeaseService = workerLeaseService;
        this.standardLibraryDiscovery = standardLibraryDiscovery;

        target(new Intel32Architecture());
        target(new Intel64Architecture());
        configInsertLocation = 0;
    }

    protected CommandLineToolSearchResult locate(GccCommandLineToolConfigurationInternal tool) {
        return toolSearchPath.locate(tool.getToolType(), tool.getExecutable());
    }

    @Override
    public List<File> getPath() {
        return toolSearchPath.getPath();
    }

    @Override
    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            toolSearchPath.path(resolve(path));
        }
    }

    protected CompilerMetaDataProvider<GccMetadata> getMetaDataProvider() {
        return metaDataProvider;
    }

    @Override
    public void target(String platformName) {
        target(platformName, Actions.<NativePlatformToolChain>doNothing());
    }

    @Override
    public void target(String platformName, Action<? super GccPlatformToolChain> action) {
        target(new DefaultTargetPlatformConfiguration(asList(platformName), action));
    }

    public void target(List<String> platformNames, Action<? super GccPlatformToolChain> action) {
        target(new DefaultTargetPlatformConfiguration(platformNames, action));
    }

    private void target(TargetPlatformConfiguration targetPlatformConfiguration) {
        platformConfigs.add(configInsertLocation, targetPlatformConfiguration);
        configInsertLocation++;
    }

    @Override
    public void setTargets(String... platformNames) {
        platformConfigs.clear();
        configInsertLocation = 0;
        for (String platformName : platformNames) {
            target(platformName);
        }
    }

    @Override
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        return select(NativeLanguage.ANY, targetPlatform);
    }

    private PlatformToolProvider getProviderForPlatform(NativePlatformInternal targetPlatform) {
        PlatformToolProvider toolProvider = toolProviders.get(targetPlatform);
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform);
            toolProviders.put(targetPlatform, toolProvider);
        }
        return toolProvider;
    }

    @Override
    public PlatformToolProvider select(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
        PlatformToolProvider toolProvider = getProviderForPlatform(targetMachine);
        switch (sourceLanguage) {
            case CPP:
                if (toolProvider instanceof UnsupportedPlatformToolProvider) {
                    return toolProvider;
                }
                ToolSearchResult cppCompiler = toolProvider.locateTool(ToolType.CPP_COMPILER);
                if (cppCompiler.isAvailable()) {
                    return toolProvider;
                }
                // No C++ compiler, complain about it
                return new UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cppCompiler);
            case ANY:
                if (toolProvider instanceof UnsupportedPlatformToolProvider) {
                    return toolProvider;
                }
                ToolSearchResult cCompiler = toolProvider.locateTool(ToolType.C_COMPILER);
                if (cCompiler.isAvailable()) {
                    return toolProvider;
                }
                ToolSearchResult compiler = toolProvider.locateTool(ToolType.CPP_COMPILER);
                if (compiler.isAvailable()) {
                    return toolProvider;
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVEC_COMPILER);
                if (compiler.isAvailable()) {
                    return toolProvider;
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVECPP_COMPILER);
                if (compiler.isAvailable()) {
                    return toolProvider;
                }
                // No compilers available, complain about the missing C compiler
                return new UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cCompiler);
            default:
                return new UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage));
        }
    }

    private PlatformToolProvider createPlatformToolProvider(NativePlatformInternal targetPlatform) {
        TargetPlatformConfiguration targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform);
        if (targetPlatformConfigurationConfiguration == null) {
            return new UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), String.format("Don't know how to build for %s.", targetPlatform.getDisplayName()));
        }

        DefaultGccPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultGccPlatformToolChain.class, targetPlatform);
        addDefaultTools(configurableToolChain);
        configureDefaultTools(configurableToolChain);
        targetPlatformConfigurationConfiguration.apply(configurableToolChain);
        configureActions.execute(configurableToolChain);
        configurableToolChain.compilerProbeArgs(standardLibraryDiscovery.compilerProbeArgs(targetPlatform));

        ToolChainAvailability result = new ToolChainAvailability();
        initTools(configurableToolChain, result);
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        return new GccPlatformToolProvider(buildOperationExecutor, targetPlatform.getOperatingSystem(), toolSearchPath, configurableToolChain, execActionFactory, compilerOutputFileNamingSchemeFactory, configurableToolChain.isCanUseCommandFile(), workerLeaseService, new CompilerMetaDataProviderWithDefaultArgs(configurableToolChain.getCompilerProbeArgs(), metaDataProvider));
    }

    protected void initTools(DefaultGccPlatformToolChain platformToolChain, ToolChainAvailability availability) {
        // Attempt to determine whether the compiler is the correct implementation
        for (GccCommandLineToolConfigurationInternal tool : platformToolChain.getCompilers()) {
            CommandLineToolSearchResult compiler = locate(tool);
            if (compiler.isAvailable()) {
                SearchResult<GccMetadata> gccMetadata = getMetaDataProvider().getCompilerMetaData(toolSearchPath.getPath(), spec -> spec.executable(compiler.getTool()).args(platformToolChain.getCompilerProbeArgs()));
                availability.mustBeAvailable(gccMetadata);
                if (!gccMetadata.isAvailable()) {
                    return;
                }
                // Assume all the other compilers are ok, if they happen to be installed
                LOGGER.debug("Found {} with version {}", tool.getToolType().getToolName(), gccMetadata);
                initForImplementation(platformToolChain, gccMetadata.getComponent());
                break;
            }
        }
    }

    protected void initForImplementation(DefaultGccPlatformToolChain platformToolChain, GccMetadata versionResult) {
    }

    private void addDefaultTools(DefaultGccPlatformToolChain toolChain) {
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

    protected void configureDefaultTools(DefaultGccPlatformToolChain toolChain) {
    }

    @Nullable
    protected TargetPlatformConfiguration getPlatformConfiguration(NativePlatformInternal targetPlatform) {
        for (TargetPlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
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

    private static class DefaultTargetPlatformConfiguration implements TargetPlatformConfiguration {
        //TODO this should be a container of platforms
        private final Collection<String> platformNames;
        private Action<? super GccPlatformToolChain> configurationAction;

        public DefaultTargetPlatformConfiguration(Collection<String> targetPlatformNames, Action<? super GccPlatformToolChain> configurationAction) {
            this.platformNames = targetPlatformNames;
            this.configurationAction = configurationAction;
        }

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return platformNames.contains(targetPlatform.getName());
        }

        @Override
        public void apply(DefaultGccPlatformToolChain platformToolChain) {
            configurationAction.execute(platformToolChain);
        }
    }

    private static class CompilerMetaDataProviderWithDefaultArgs implements CompilerMetaDataProvider<GccMetadata> {

        private final List<String> compilerProbeArgs;
        private final CompilerMetaDataProvider<GccMetadata> delegate;

        public CompilerMetaDataProviderWithDefaultArgs(List<String> compilerProbeArgs, CompilerMetaDataProvider<GccMetadata> delegate) {
            this.compilerProbeArgs = compilerProbeArgs;
            this.delegate = delegate;
        }

        @Override
        public SearchResult<GccMetadata> getCompilerMetaData(List<File> searchPath, Action<? super CompilerExecSpec> configureAction) {
            return delegate.getCompilerMetaData(searchPath, execSpec -> {
                execSpec.args(compilerProbeArgs);
                configureAction.execute(execSpec);
            });
        }

        @Override
        public CompilerType getCompilerType() {
            return delegate.getCompilerType();
        }
    }

}
