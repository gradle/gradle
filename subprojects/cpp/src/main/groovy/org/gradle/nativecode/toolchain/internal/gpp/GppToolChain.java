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
package org.gradle.nativecode.toolchain.internal.gpp;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.Factory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.base.internal.BinaryCompileSpec;
import org.gradle.nativecode.base.internal.ToolChainInternal;
import org.gradle.nativecode.toolchain.internal.gpp.version.GppVersionDeterminer;
import org.gradle.nativecode.language.cpp.internal.CppCompileSpec;
import org.gradle.nativecode.base.internal.LinkerSpec;
import org.gradle.nativecode.base.internal.StaticLibraryArchiverSpec;
import org.gradle.process.internal.ExecAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Compiler adapter for g++
 */
public class GppToolChain implements ToolChainInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppToolChain.class);

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

    public GppToolChain(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem.findInPath(GPP), operatingSystem.findInPath(AR), operatingSystem, execActionFactory, new GppVersionDeterminer());
    }

    protected GppToolChain(File gppExecutable, File arExecutable, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory, Transformer<String, File> versionDeterminer) {
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

    public <T extends BinaryCompileSpec> Compiler<T> createCompiler(Class<T> specType) {
        checkAvailable();
        if (CppCompileSpec.class.isAssignableFrom(specType)) {
            return (Compiler<T>) new GppCompiler(gppExecutable, execActionFactory, canUseCommandFile(version));
        }
        throw new IllegalArgumentException(String.format("No suitable compiler available for %s.", specType));
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        checkAvailable();
        return (Compiler<T>) new GppLinker(gppExecutable, execActionFactory, canUseCommandFile(version));
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        checkAvailable();
        return (Compiler<T>) new ArStaticLibraryArchiver(arExecutable, execActionFactory);
    }

    private void checkAvailable() {
        if (version == null) {
            throw new IllegalStateException(String.format("Tool chain %s is not available", getName()));
        }
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