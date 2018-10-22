/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.util.DeprecationLogger;

import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

public class DefaultBasePluginConvention extends BasePluginConvention implements HasPublicType {
    private ProjectInternal project;

    private String distsDirName;

    private String libsDirName;

    // cached resolved values
    private File buildDir;
    private File libsDir;
    private File distsDir;

    private String archivesBaseName;

    public DefaultBasePluginConvention(Project project) {
        this.project = (ProjectInternal) project;
        archivesBaseName = project.getName();
        distsDirName = "distributions";
        libsDirName = "libs";
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(BasePluginConvention.class);
    }

    @Override
    @Deprecated
    public File getDistsDir() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("distsDir");
        File curProjectBuildDir = project.getBuildDir();
        if (distsDir != null && curProjectBuildDir.equals(buildDir)) {
            return distsDir;
        }
        buildDir = curProjectBuildDir;
        File dir = project.getServices().get(FileLookup.class).getFileResolver(curProjectBuildDir).resolve(distsDirName);
        distsDir = dir;
        return dir;
    }

    @Override
    @Deprecated
    public File getLibsDir() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("libsDir");
        File curProjectBuildDir = project.getBuildDir();
        if (libsDir != null && curProjectBuildDir.equals(buildDir)) {
            return libsDir;
        }
        buildDir = curProjectBuildDir;
        File dir = project.getServices().get(FileLookup.class).getFileResolver(curProjectBuildDir).resolve(libsDirName);
        libsDir = dir;
        return dir;
    }

    @Override
    public ProjectInternal getProject() {
        return project;
    }

    @Override
    public void setProject(ProjectInternal project) {
        this.project = project;
    }

    @Override
    public String getDistsDirName() {
        return distsDirName;
    }

    @Override
    public void setDistsDirName(String distsDirName) {
        this.distsDirName = distsDirName;
        this.distsDir = null;
    }

    @Override
    public String getLibsDirName() {
        return libsDirName;
    }

    @Override
    public void setLibsDirName(String libsDirName) {
        this.libsDirName = libsDirName;
        this.libsDir = null;
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
