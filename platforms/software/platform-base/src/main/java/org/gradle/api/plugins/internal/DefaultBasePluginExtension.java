/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.provider.Property;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.RelativePathUtil;

import java.io.File;

public class DefaultBasePluginExtension implements BasePluginExtension {

    private final Project project;
    private final DirectoryProperty distsDirectory;
    private final DirectoryProperty libsDirectory;
    private final Property<String> archivesName;

    public DefaultBasePluginExtension(Project project) {
        this.project = project;
        this.distsDirectory = project.getObjects().directoryProperty();
        this.libsDirectory = project.getObjects().directoryProperty();
        this.archivesName = project.getObjects().property(String.class);
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
    public Property<String> getArchivesName() {
        return archivesName;
    }

    @Override
    @Deprecated
    public String getDistsDirName() {
        logPropertyDeprecation("distsDirName", "distsDirectory");
        File buildDir = project.getLayout().getBuildDirectory().get().getAsFile();
        File distsDir = getDistsDirectory().get().getAsFile();
        return RelativePathUtil.relativePath(buildDir, distsDir);
    }

    @Override
    @Deprecated
    public void setDistsDirName(String distsDirName) {
        logPropertyDeprecation("distsDirName", "distsDirectory");
        getDistsDirectory().set(project.getLayout().getBuildDirectory().dir(distsDirName));
    }

    @Override
    @Deprecated
    public String getLibsDirName() {
        logPropertyDeprecation("libsDirName", "libsDirectory");
        File buildDir = project.getLayout().getBuildDirectory().get().getAsFile();
        File libsDir = getLibsDirectory().get().getAsFile();
        return RelativePathUtil.relativePath(buildDir, libsDir);
    }

    @Override
    @Deprecated
    public void setLibsDirName(String libsDirName) {
        logPropertyDeprecation("libsDirName", "libsDirectory");
        getLibsDirectory().set(project.getLayout().getBuildDirectory().dir(libsDirName));
    }

    @Override
    @Deprecated
    public String getArchivesBaseName() {
        logPropertyDeprecation("archivesBaseName", "archivesName");
        return getArchivesName().get();
    }

    @Override
    @Deprecated
    public void setArchivesBaseName(String archivesBaseName) {
        logPropertyDeprecation("archivesBaseName", "archivesName");
        getArchivesName().set(archivesBaseName);
    }

    private static void logPropertyDeprecation(String propertyName, String replacementPropertyName) {
        DeprecationLogger.deprecateProperty(BasePluginExtension.class, propertyName)
            .replaceWith(replacementPropertyName)
            .willBeRemovedInGradle9()
            .withDslReference(BasePluginExtension.class, replacementPropertyName)
            .nagUser();
    }
}
