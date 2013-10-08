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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.ToolChainAvailability;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.GccTool;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Compiler adapter for GCC.
 */
public class GccToolChain extends AbstractGccCompatibleToolChain implements Gcc {

    private static final Logger LOGGER = LoggerFactory.getLogger(GccToolChain.class);

    public static final String DEFAULT_NAME = "gcc";

    private final Transformer<String, File> versionDeterminer;

    private String version;

    public GccToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory) {
        super(name, operatingSystem, fileResolver, execActionFactory, new GccToolRegistry(operatingSystem));
        this.versionDeterminer = new GccVersionDeterminer(execActionFactory);

        tools.setExeName(ToolType.CPP_COMPILER, "g++");
        tools.setExeName(ToolType.C_COMPILER, "gcc");
        tools.setExeName(ToolType.ASSEMBLER, "as");
        tools.setExeName(ToolType.LINKER, "g++");
        tools.setExeName(ToolType.STATIC_LIB_ARCHIVER, "ar");
    }

    @Override
    protected String getTypeName() {
        return "GNU G++";
    }

    public List<File> getPath() {
        return tools.getPath();
    }

    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            tools.path(resolve(path));
        }
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        super.checkAvailable(availability);
        determineVersion();
        if (version == null) {
            availability.unavailable("Could not determine G++ version");
        }
    }

    private void determineVersion() {
        version = determineVersion(tools.locate(ToolType.CPP_COMPILER));
        if (version == null) {
            LOGGER.info("Did not find {} on system", ToolType.CPP_COMPILER.getToolName());
        } else {
            LOGGER.info("Found {} with version {}", ToolType.CPP_COMPILER.getToolName(), version);
        }
    }

    private String determineVersion(File executable) {
        return executable == null ? null : versionDeterminer.transform(executable);
    }

    public GccTool getCppCompiler() {
        return new DefaultTool(ToolType.CPP_COMPILER);
    }

    public GccTool getCCompiler() {
        return new DefaultTool(ToolType.C_COMPILER);
    }

    public GccTool getAssembler() {
        return new DefaultTool(ToolType.ASSEMBLER);
    }

    public GccTool getLinker() {
        return new DefaultTool(ToolType.LINKER);
    }

    public GccTool getStaticLibArchiver() {
        return new DefaultTool(ToolType.STATIC_LIB_ARCHIVER);
    }

    protected boolean canUseCommandFile() {
        String[] components = version.split("\\.");
        int majorVersion;
        try {
            majorVersion = Integer.valueOf(components[0]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("Unable to determine major g++ version from version number %s.", version), e);
        }
        return majorVersion >= 4;
    }

    private class DefaultTool implements GccTool {
        private final ToolType toolType;

        private DefaultTool(ToolType toolType) {
            this.toolType = toolType;
        }

        public String getExecutable() {
            return tools.getExeName(toolType);
        }

        public void setExecutable(String file) {
            tools.setExeName(toolType, file);
        }
    }

}