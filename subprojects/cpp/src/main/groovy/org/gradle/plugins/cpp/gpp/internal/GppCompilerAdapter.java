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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.internal.CompilerAdapter;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Compiler adapter for g++
 */
public class GppCompilerAdapter implements CompilerAdapter<GppCompileSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GppCompilerAdapter.class);

    public static final String NAME = "gpp";
    private Compiler<GppCompileSpec> compiler;
    private boolean searched;
    private String version;
    private final Transformer<String, File> versionDeterminer;

    public GppCompilerAdapter(ProjectInternal project, Transformer<String, File> versionDeterminer) {
        compiler = new GppCompiler(project.getFileResolver());
        this.versionDeterminer = versionDeterminer;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public String toString() {
        return String.format("GNU G++ (%s)", OperatingSystem.current().getExecutableName(GppCompiler.EXECUTABLE));
    }

    public boolean isAvailable() {
        String version = getVersion();
        return version != null;
    }

    public Compiler<GppCompileSpec> createCompiler(Binary binary) {
        return compiler;
    }

    private String getVersion() {
        if (!searched) {
            searched = true;
            version = determineVersion();
            if (version == null) {
                LOGGER.info("Did not find {} on system", GppCompiler.EXECUTABLE);
            } else {
                LOGGER.info("Found {} with version {}", GppCompiler.EXECUTABLE, version);
            }
        }
        return version;
    }

    private String determineVersion() {
        File binary = OperatingSystem.current().findInPath(GppCompiler.EXECUTABLE);
        return binary == null ? null : versionDeterminer.transform(binary);
    }
}