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
import org.gradle.plugins.cpp.compiler.internal.CommandLineCppCompilerAdapter;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.plugins.cpp.gpp.internal.version.GppVersionDeterminer;
import org.gradle.process.internal.ExecAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Compiler adapter for g++
 */
public class GppCompilerAdapter extends CommandLineCppCompilerAdapter<GppCompileSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppCompilerAdapter.class);

    static final String EXECUTABLE = "g++";
    
    public static final String NAME = "gpp";

    private boolean determinedVersion;
    private String version;

    private final Transformer<String, File> versionDeterminer;

    public GppCompilerAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory) {
        this(operatingSystem, execActionFactory, new GppVersionDeterminer());
    }

    GppCompilerAdapter(OperatingSystem operatingSystem, Factory<ExecAction> execActionFactory, Transformer<String, File> versionDeterminer) {
        super(EXECUTABLE, operatingSystem, execActionFactory);
        this.versionDeterminer = versionDeterminer;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return String.format("GNU G++ (%s)", getOperatingSystem().getExecutableName(EXECUTABLE));
    }

    public boolean isAvailable() {
        String version = getVersion();
        return version != null;
    }

    public Compiler<GppCompileSpec> createCompiler() {
        String version = getVersion();
        if (version == null) {
            throw new IllegalStateException("Cannot create gpp compiler when it is not available");
        }
        
        String[] components = version.split("\\.");

        int majorVersion;
        try {
            majorVersion = Integer.valueOf(components[0]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(String.format("Unable to determine major g++ version from version number %s.", version), e);
        }

        return new GppCompiler(getExecutable(), getExecActionFactory(), majorVersion >= 4);
    }

    private String getVersion() {
        if (!determinedVersion) {
            determinedVersion = true;
            version = determineVersion(getExecutable());
            if (version == null) {
                LOGGER.info("Did not find {} on system", EXECUTABLE);
            } else {
                LOGGER.info("Found {} with version {}", EXECUTABLE, version);
            }
        }
        return version;
    }

    private String determineVersion(File executable) {
        return executable == null ? null : versionDeterminer.transform(executable);
    }
    
}