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

package org.gradle.language.cpp.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppExecutable;

import javax.inject.Inject;

public class DefaultCppExecutable extends DefaultCppBinary implements CppExecutable {
    private final RegularFileProperty executableFile;
    private final DirectoryProperty installationDirectory;

    @Inject
    public DefaultCppExecutable(String name, ProjectLayout projectLayout, ObjectFactory objectFactory, Provider<String> baseName, boolean debuggable, FileCollection sourceFiles, FileCollection componentHeaderDirs, ConfigurationContainer configurations, Configuration implementation) {
        super(name, projectLayout, objectFactory, baseName, debuggable, sourceFiles, componentHeaderDirs, configurations, implementation);
        this.executableFile = projectLayout.fileProperty();
        this.installationDirectory = projectLayout.directoryProperty();
    }

    @Override
    public RegularFileProperty getExecutableFile() {
        return executableFile;
    }

    @Override
    public DirectoryProperty getInstallDirectory() {
        return installationDirectory;
    }
}
