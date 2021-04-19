/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginExtension;

public class DefaultBasePluginExtension implements BasePluginExtension {
    private final DirectoryProperty buildDirectory;
    private final DirectoryProperty distsDirectory;
    private final DirectoryProperty libsDirectory;

    private String distsDirName;
    private String libsDirName;
    private String archivesBaseName;

    public DefaultBasePluginExtension(Project project) {
        this.buildDirectory = project.getLayout().getBuildDirectory();
        this.archivesBaseName = project.getName();

        this.distsDirName = "distributions";
        this.distsDirectory = project.getObjects().directoryProperty();
        distsDirectory.convention(buildDirectory.dir(distsDirName));

        this.libsDirName = "libs";
        this.libsDirectory = project.getObjects().directoryProperty();
        libsDirectory.convention(buildDirectory.dir(libsDirName));
    }

    @Override
    public DirectoryProperty getDistsDirectory() {
        return distsDirectory;
    }

    @Override
    public DirectoryProperty getLibsDirectory() {
        return libsDirectory;
    }

    @Override
    public String getDistsDirName() {
        return distsDirName;
    }

    @Override
    public void setDistsDirName(String distsDirName) {
        this.distsDirName = distsDirName;
        distsDirectory.set(buildDirectory.dir(distsDirName));
    }

    @Override
    public String getLibsDirName() {
        return libsDirName;
    }

    @Override
    public void setLibsDirName(String libsDirName) {
        this.libsDirName = libsDirName;
        libsDirectory.set(buildDirectory.dir(libsDirName));
    }

    @Override
    public String getArchivesBaseName() {
        return archivesBaseName;
    }

    @Override
    public void setArchivesBaseName(String archivesBaseName) {
        this.archivesBaseName = archivesBaseName;
    }
}
