/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins;

import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;

/**
 * <p>A {@link Convention} used for the BasePlugin.</p>
 */
public class BasePluginConvention extends GroovyObjectSupport {
    private ProjectInternal project;

    private String distsDirName;

    private String libsDirName;

    private String archivesBaseName;
    public BasePluginConvention(Project project) {
        this.project = (ProjectInternal) project;
        archivesBaseName = project.getName();
        distsDirName = "distributions";
        libsDirName = "libs";
    }

    /**
     * Returns the directory to generate TAR and ZIP archives into.
     *
     * @return The directory. Never returns null.
     */
    public File getDistsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(distsDirName);
    }

    /**
     * Returns the directory to generate JAR and WAR archives into.
     *
     * @return The directory. Never returns null.
     */
    public File getLibsDir() {
        return project.getServices().get(FileLookup.class).getFileResolver(project.getBuildDir()).resolve(libsDirName);
    }

    public ProjectInternal getProject() {
        return project;
    }

    public void setProject(ProjectInternal project) {
        this.project = project;
    }

    /**
     * The name for the distributions directory. This in interpreted relative to the project' build directory.
     */
    public String getDistsDirName() {
        return distsDirName;
    }

    public void setDistsDirName(String distsDirName) {
        this.distsDirName = distsDirName;
    }

    /**
     * The name for the libs directory. This in interpreted relative to the project' build directory.
     */
    public String getLibsDirName() {
        return libsDirName;
    }

    public void setLibsDirName(String libsDirName) {
        this.libsDirName = libsDirName;
    }

    /**
     * The base name to use for archive files.
     */
    public String getArchivesBaseName() {
        return archivesBaseName;
    }

    public void setArchivesBaseName(String archivesBaseName) {
        this.archivesBaseName = archivesBaseName;
    }
}
