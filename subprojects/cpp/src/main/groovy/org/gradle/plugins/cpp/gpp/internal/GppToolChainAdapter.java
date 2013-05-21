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
import org.gradle.plugins.binaries.model.BinaryCompileSpec;
import org.gradle.plugins.binaries.model.ToolChain;
import org.gradle.plugins.binaries.model.ToolChainAdapter;
import org.gradle.plugins.cpp.gpp.internal.version.GppVersionDeterminer;
import org.gradle.plugins.cpp.internal.CppCompileSpec;
import org.gradle.process.internal.ExecAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Compiler adapter for g++
 */
public class GppToolChainAdapter implements ToolChainAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppToolChainAdapter.class);

    static final String EXECUTABLE = "g++";
    public static final String NAME = "gpp";

    private final File executable;
    private final OperatingSystem operatingSystem;
    private final Factory<ExecAction> execActionFactory;
    private final Transformer<String, File> versionDeterminer;

    private boolean determinedVersion;
    private String version;

    public GppToolChainAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem.findInPath(EXECUTABLE), operatingSystem, execActionFactory, new GppVersionDeterminer());
    }

    protected GppToolChainAdapter(File executable, OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory, Transformer<String, File> versionDeterminer) {
        this.executable = executable;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
        this.versionDeterminer = versionDeterminer;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return String.format("GNU G++ (%s)", operatingSystem.getExecutableName(EXECUTABLE));
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
                determineVersion();
                if (version == null) {
                    throw new IllegalStateException("Cannot create gpp compiler when it is not available");
                }

                return (Compiler<T>) new GppCompiler(executable, execActionFactory, canUseCommandFile(version));
            }
        };
    }

    private void determineVersion() {
        if (!determinedVersion) {
            determinedVersion = true;
            version = determineVersion(executable);
            if (version == null) {
                LOGGER.info("Did not find {} on system", EXECUTABLE);
            } else {
                LOGGER.info("Found {} with version {}", EXECUTABLE, version);
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