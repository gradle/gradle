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
package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.*;
import org.gradle.plugins.cpp.gpp.internal.version.GppVersionDeterminer;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.plugins.cpp.internal.LinkerSpec;
import org.gradle.process.internal.ExecAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Compiler adapter for g++
 */
public class GppToolChainAdapter implements ToolChainAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppToolChainAdapter.class);

    public static final String NAME = "gpp";
    private static final String GPP = "g++";
    private static final String AR = "ar";

    private final File gppExecutable;
    private final File arExecutable;
    private final OperatingSystem operatingSystem;
    private final Factory<ExecAction> execActionFactory;
    private final Transformer<String, File> versionDeterminer;

    private boolean determinedVersion;
    private String version;

    public GppToolChainAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem.findInPath(GPP), operatingSystem.findInPath(AR), operatingSystem, execActionFactory, new GppVersionDeterminer());
    }

    protected GppToolChainAdapter(File gppExecutable, File arExecutable, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory, Transformer<String, File> versionDeterminer) {
        this.gppExecutable = gppExecutable;
        this.arExecutable = arExecutable;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
        this.versionDeterminer = versionDeterminer;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return String.format("GNU G++ (%s)", operatingSystem.getExecutableName(GPP));
    }

    public boolean isAvailable() {
        determineVersion();
        return version != null;
    }

    public ToolChain create() {
        return new ToolChain() {
            public <T extends BinaryCompileSpec> Compiler<T> createCompiler(Class<T> specType) {
                if (!specType.isAssignableFrom(CppCompileSpec.class)) {
                    // TODO:DAZ Should introduce language instead of relying on spec here
                    throw new IllegalArgumentException(String.format("No suitable compiler available for %s.", specType));
                }
                // TODO:DAZ Move this prior to ToolChain creation, and extract GppToolChain (given executables)
                determineVersion();
                if (version == null) {
                    throw new IllegalStateException("Cannot create gpp compiler when it is not available");
                }

                return (Compiler<T>) new GppCompiler(gppExecutable, execActionFactory, canUseCommandFile(version));
            }

            public Compiler<? super LinkerSpec> createLinker(NativeBinary output) {
                if (output instanceof StaticLibraryBinary) {
                    return new ArStaticLibraryLinker(arExecutable, execActionFactory);
                }
                if (output instanceof SharedLibraryBinary) {
                    return new GppSharedLibraryLinker(gppExecutable, execActionFactory, canUseCommandFile(version));
                }
                return new GppExecutableLinker(gppExecutable, execActionFactory, canUseCommandFile(version));
            }
        };
    }

    private void determineVersion() {
        if (!determinedVersion) {
            determinedVersion = true;
            version = determineVersion(gppExecutable);
            if (version == null) {
                LOGGER.info("Did not find {} on system", GPP);
            } else {
                LOGGER.info("Found {} with version {}", GPP, version);
            }
        }
    }

    private String determineVersion(File executable) {
        return executable == null ? null : versionDeterminer.transform(executable);
    }

    private boolean canUseCommandFile(String version) {
        String[] components = version.split("\\.");
        int majorVersion;
        try {
            majorVersion = Integer.valueOf(components[0]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("Unable to determine major g++ version from version number %s.", version), e);
        }
        return majorVersion >= 4;
    }

}