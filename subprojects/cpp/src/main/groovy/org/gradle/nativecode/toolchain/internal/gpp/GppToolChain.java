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
    private static final String GCC = "gcc";
    private static final String AR = "ar";

    private final File gppExecutable;
    private final File gccExecutable;
    private final File arExecutable;
    private final Factory<ExecAction> execActionFactory;
    private final Transformer<String, File> versionDeterminer;

    private String version;

    public GppToolChain(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        super(operatingSystem);
        gppExecutable = findExecutable(operatingSystem, GPP);
        gccExecutable = findExecutable(operatingSystem, GCC);
        arExecutable = operatingSystem.findInPath(AR);
        this.execActionFactory = execActionFactory;
        this.versionDeterminer = new GppVersionDeterminer();
    }

    private static File findExecutable(OperatingSystem operatingSystem, String exe) {
        List<String> candidates;
        if (operatingSystem.isWindows()) {
            // Under Cygwin, g++/gcc is a Cygwin symlink to either g++-3 or g++-4. We can't run g++ directly
            candidates = Arrays.asList(exe + "-4", exe + "-3", exe);
        } else {
            candidates = Arrays.asList(exe);
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

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        availability.mustExist(GPP, gppExecutable);
        availability.mustExist(GCC, gccExecutable);
        availability.mustExist(AR, arExecutable);
        determineVersion();
        if (version == null) {
            availability.unavailable("Could not determine G++ version");
        }
    }

    public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
        checkAvailable();
        return (Compiler<T>) new CppCompiler(gppExecutable, execActionFactory, canUseCommandFile(version));
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        checkAvailable();
        return (Compiler<T>) new CCompiler(gccExecutable, execActionFactory, canUseCommandFile(version));
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        checkAvailable();
        return (Compiler<T>) new GppLinker(gppExecutable, execActionFactory, canUseCommandFile(version));
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        checkAvailable();
        return (Compiler<T>) new ArStaticLibraryArchiver(arExecutable, execActionFactory);
    }

    private void determineVersion() {
        version = determineVersion(gppExecutable);
        if (version == null) {
            LOGGER.info("Did not find {} on system", GPP);
        } else {
            LOGGER.info("Found {} with version {}", GPP, version);
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