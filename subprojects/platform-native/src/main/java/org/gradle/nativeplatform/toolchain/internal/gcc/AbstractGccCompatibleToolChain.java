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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.GccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionResult;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * A tool chain that has GCC semantics.
 */
public abstract class AbstractGccCompatibleToolChain extends ExtendableToolChain<GccPlatformToolChain> implements GccCompatibleToolChain {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGccCompatibleToolChain.class);
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final ExecActionFactory execActionFactory;
    private final ToolSearchPath toolSearchPath;
    private final List<TargetPlatformConfiguration> platformConfigs = new ArrayList<TargetPlatformConfiguration>();
    private final Map<NativePlatform, PlatformToolProvider> toolProviders = Maps.newHashMap();
    private final CompilerMetaDataProvider metaDataProvider;
    private final Instantiator instantiator;
    private int configInsertLocation;

    public AbstractGccCompatibleToolChain(String name, BuildOperationProcessor buildOperationProcessor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProvider metaDataProvider, Instantiator instantiator) {
        this(name, buildOperationProcessor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, new ToolSearchPath(operatingSystem), metaDataProvider, instantiator);
    }

    AbstractGccCompatibleToolChain(String name, BuildOperationProcessor buildOperationProcessor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, ToolSearchPath tools, CompilerMetaDataProvider metaDataProvider, Instantiator instantiator) {
        super(name, buildOperationProcessor, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.toolSearchPath = tools;
        this.metaDataProvider = metaDataProvider;
        this.instantiator = instantiator;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;

        target(new Intel32Architecture());
        target(new Intel64Architecture());
        configInsertLocation = 0;
    }

    protected CommandLineToolSearchResult locate(GccCommandLineToolConfigurationInternal gccTool) {
        return toolSearchPath.locate(gccTool.getToolType(), gccTool.getExecutable());
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

    protected CompilerMetaDataProvider getMetaDataProvider() {
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
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        PlatformToolProvider toolProvider = toolProviders.get(targetPlatform);
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform);
            toolProviders.put(targetPlatform, toolProvider);
        }
        return toolProvider;
    }

    private PlatformToolProvider createPlatformToolProvider(NativePlatformInternal targetPlatform) {
        TargetPlatformConfiguration targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform);
        ToolChainAvailability result = new ToolChainAvailability();
        if (targetPlatformConfigurationConfiguration == null) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        DefaultGccPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultGccPlatformToolChain.class, targetPlatform);
        addDefaultTools(configurableToolChain);
        configureDefaultTools(configurableToolChain);
        targetPlatformConfigurationConfiguration.apply(configurableToolChain);
        configureActions.execute(configurableToolChain);

        initTools(configurableToolChain, result);
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        return new GccPlatformToolProvider(buildOperationProcessor, targetPlatform.getOperatingSystem(), toolSearchPath, configurableToolChain, execActionFactory, compilerOutputFileNamingSchemeFactory, configurableToolChain.isCanUseCommandFile());
    }

    protected void initTools(DefaultGccPlatformToolChain platformToolChain, ToolChainAvailability availability) {
        // Attempt to determine whether the compiler is the correct implementation
        boolean found = false;
        for (GccCommandLineToolConfigurationInternal tool : platformToolChain.getCompilers()) {
            CommandLineToolSearchResult compiler = locate(tool);
            if (compiler.isAvailable()) {
                GccVersionResult versionResult = getMetaDataProvider().getGccMetaData(compiler.getTool(), platformToolChain.getCompilerProbeArgs());
                availability.mustBeAvailable(versionResult);
                if (!versionResult.isAvailable()) {
                    return;
                }
                // Assume all the other compilers are ok, if they happen to be installed
                LOGGER.debug("Found {} with version {}", ToolType.C_COMPILER.getToolName(), versionResult);
                found = true;
                initForImplementation(platformToolChain, versionResult);
                break;
            }
        }

        // Attempt to locate each tool
        for (GccCommandLineToolConfigurationInternal tool : platformToolChain.getTools()) {
            found |= toolSearchPath.locate(tool.getToolType(), tool.getExecutable()).isAvailable();
        }
        if (!found) {
            // No tools found - report just the C compiler as missing
            // TODO - report whichever tool is actually required, eg if there's only assembler source, complain about the assembler
            GccCommandLineToolConfigurationInternal cCompiler = platformToolChain.getcCompiler();
            availability.mustBeAvailable(locate(cCompiler));
        }
    }

    protected void initForImplementation(DefaultGccPlatformToolChain platformToolChain, GccVersionResult versionResult) {
    }

    private void addDefaultTools(DefaultGccPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, "ar"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVECPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVEC_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, "gcc"));
    }

    protected void configureDefaultTools(DefaultGccPlatformToolChain toolChain) {
    }

    protected TargetPlatformConfiguration getPlatformConfiguration(NativePlatformInternal targetPlatform) {
        for (TargetPlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
    }

    private class Intel32Architecture implements TargetPlatformConfiguration {

        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent() && targetPlatform.getArchitecture().isI386();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain gccToolChain) {
            gccToolChain.compilerProbeArgs("-m32");
            Action<List<String>> m32args = new Action<List<String>>() {
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

    private class Intel64Architecture implements TargetPlatformConfiguration {
        @Override
        public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && targetPlatform.getArchitecture().isAmd64();
        }

        @Override
        public void apply(DefaultGccPlatformToolChain gccToolChain) {
            gccToolChain.compilerProbeArgs("-m64");
            Action<List<String>> m64args = new Action<List<String>>() {
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
}
