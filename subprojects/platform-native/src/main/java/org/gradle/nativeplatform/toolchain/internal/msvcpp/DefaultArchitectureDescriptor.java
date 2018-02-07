/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import java.io.File;
import java.util.List;
import java.util.Map;

public class DefaultArchitectureDescriptor implements ArchitectureDescriptor {
    private final List<File> paths;
    private final File binPath;
    private final File libPath;
    private final File includePath;
    private final String assemblerFilename;
    private final Map<String, String> definitions;
    private final File compilerPath;

    DefaultArchitectureDescriptor(List<File> paths, File binPath, File libPath, File compilerPath, File includePath, String assemblerFilename, Map<String, String> definitions) {
        this.paths = paths;
        this.binPath = binPath;
        this.libPath = libPath;
        this.includePath = includePath;
        this.assemblerFilename = assemblerFilename;
        this.definitions = definitions;
        this.compilerPath = compilerPath;
    }

    @Override
    public List<File> getPaths() {
        return paths;
    }

    @Override
    public File getBinaryPath() {
        return binPath;
    }

    @Override
    public File getLibraryPath() {
        return libPath;
    }

    @Override
    public File getIncludePath() {
        return includePath;
    }

    @Override
    public String getAssemblerFilename() {
        return assemblerFilename;
    }

    @Override
    public Map<String, String> getDefinitions() {
        return definitions;
    }

    @Override
    public boolean isInstalled() {
        return binPath.exists() && compilerPath.exists() && libPath.exists();
    }
}
