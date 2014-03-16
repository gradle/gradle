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
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.project.ProjectInternal

public class BasePluginConvention {
    ProjectInternal project

    /**
     * The name for the distributions directory. This in interpreted relative to the project' build directory.
     */
    String distsDirName

    /**
     * The name for the libs directory. This in interpreted relative to the project' build directory.
     */
    String libsDirName

    /**
     * The base name to use for archive files.
     */
    String archivesBaseName

    BasePluginConvention(Project project) {
        this.project = project
        archivesBaseName = project.name
        distsDirName = 'distributions'
        libsDirName = 'libs'
    }

    /**
     * Returns the directory to generate TAR and ZIP archives into.
     *
     * @return The directory. Never returns null.
     */
    File getDistsDir() {
        project.services.get(FileLookup).getFileResolver(project.buildDir).resolve(distsDirName)
    }

    /**
     * Returns the directory to generate JAR and WAR archives into.
     *
     * @return The directory. Never returns null.
     */
    File getLibsDir() {
        project.services.get(FileLookup).getFileResolver(project.buildDir).resolve(libsDirName)
    }
}