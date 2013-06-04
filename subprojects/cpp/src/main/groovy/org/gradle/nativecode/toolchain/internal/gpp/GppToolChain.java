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
import org.gradle.nativecode.base.internal.*;
import org.gradle.nativecode.language.cpp.internal.CppCompileSpec;
import org.gradle.nativecode.toolchain.internal.gpp.version.GppVersionDeterminer;
import org.gradle.process.internal.ExecAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Compiler adapter for GCC.
 */
public class GppToolChain extends AbstractToolChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppToolChain.class);

    public static final String NAME = "gcc";
    private static final String GPP = "g++";
    private static final String AR = "ar";

    private final File gppExecutable;
    private final File arExecutable;
    private final Factory<ExecAction> execActionFactory;
    private final Transformer<String, File> versionDeterminer;

    private boolean determinedVersion;
    private String version;

    public GppToolChain(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(findExecutable(operatingSystem), operatingSystem.findInPath(AR), execActionFactory, new GppVersionDeterminer());
    }

    protected GppToolChain(File gppExecutable, File arExecutable, Factory<ExecAction> execActionFactory, Transformer<String, File> versionDeterminer) {
        this.gppExecutable = gppExecutable;
        this.arExecutable = arExecutable;
        this.execActionFactory = execActionFactory;
        this.versionDeterminer = versionDeterminer;
    }

    private static File findExecutable(OperatingSystem operatingSystem) {
        List<String> candidates;
        if (operatingSystem.isWindows()) {
            // Under Cygwin, g++ is a Cygwin symlink to either g++-3 or g++-4. We can't run g++ directly
            candidates = Arrays.asList("g++-4", "g++-3", GPP);
        } else {
            candidates = Arrays.asList(GPP);
        }
        for (String candidate : candidates) {
            File executable = operatingSystem.findInPath(candidate);
            if (executable != null) {
                return executable;
            }
        }
        return null;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return "GNU G++";
    }

    public ToolChainAvailability getAvailability() {
        ToolChainAvailability availability = new ToolChainAvailability();
        availability.mustExist(GPP, gppExecutable);
        availability.mustExist(AR, arExecutable);
        determineVersion();
        if (version == null) {
            availability.unavailable("Could not determine G++ version");
        }
        return availability;
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