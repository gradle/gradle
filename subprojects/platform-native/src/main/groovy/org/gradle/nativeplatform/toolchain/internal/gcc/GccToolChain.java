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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.internal.ToolChainAvailability;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionResult;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Compiler adapter for GCC.
 */
public class GccToolChain extends AbstractGccCompatibleToolChain implements Gcc {

    private static final Logger LOGGER = LoggerFactory.getLogger(GccToolChain.class);

    public static final String DEFAULT_NAME = "gcc";

    private final GccVersionDeterminer versionDeterminer;
    private final Instantiator instantiator;

    private GccVersionResult versionResult;

    public GccToolChain(Instantiator instantiator, String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory) {
        super(name, operatingSystem, fileResolver, execActionFactory, new GccToolSearchPath(operatingSystem), instantiator);
        this.instantiator = instantiator;
        this.versionDeterminer = GccVersionDeterminer.forGcc(execActionFactory);
    }

    @Override
    protected void addDefaultTools(DefaultGccPlatformToolChain toolChain) {
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.C_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.CPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.LINKER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER, "ar"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVECPP_COMPILER, "g++"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.OBJECTIVEC_COMPILER, "gcc"));
        toolChain.add(instantiator.newInstance(DefaultGccCommandLineToolConfiguration.class, ToolType.ASSEMBLER, "as"));
    }

    @Override
    protected String getTypeName() {
        return "GNU GCC";
    }

    @Override
    protected void initTools(DefaultGccPlatformToolChain platformToolChain, ToolChainAvailability availability) {
        if (versionResult == null) {
            CommandLineToolSearchResult compiler = locate(platformToolChain.getcCompiler());
            if (!compiler.isAvailable()) {
                compiler = locate(platformToolChain.getCppCompiler());
            }
            availability.mustBeAvailable(compiler);
            if (!compiler.isAvailable()) {
                return;
            }
            versionResult = versionDeterminer.getGccMetaData(compiler.getTool());
            LOGGER.debug("Found {} with version {}", ToolType.C_COMPILER.getToolName(), versionResult);
        }
        availability.mustBeAvailable(versionResult);
    }

    protected boolean canUseCommandFile() {
        return versionResult.getVersion().getMajor() >= 4;
    }
}