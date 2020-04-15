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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;

/**
 * <p>A {@link Convention} used for the BasePlugin.</p>
 */
public abstract class BasePluginConvention {
    /**
     * Returns the directory to generate TAR and ZIP archives into.
     *
     * <p>
     * This property has been replaced by distsDirectory.
     * </p>
     *
     * @return The directory. Never returns null.
     */
    @Deprecated
    public abstract File getDistsDir();

    /**
     * Returns the directory to generate TAR and ZIP archives into.
     *
     * @return The directory. Never returns null.
     *
     * @since 6.0
     */
    public abstract DirectoryProperty getDistsDirectory();

    /**
     * Returns the directory to generate JAR and WAR archives into.
     *
     * <p>
     * This property has been replaced by libsDirectory.
     * </p>
     *
     * @return The directory. Never returns null.
     */
    @Deprecated
    public abstract File getLibsDir();

    /**
     * Returns the directory to generate JAR and WAR archives into.
     *
     * @return The directory. Never returns null.
     *
     * @since 6.0
     */
    public abstract DirectoryProperty getLibsDirectory();

    @Deprecated
    public abstract ProjectInternal getProject();

    @Deprecated
    public abstract void setProject(ProjectInternal project);

    /**
     * The name for the distributions directory. This in interpreted relative to the project' build directory.
     */
    public abstract String getDistsDirName();

    public abstract void setDistsDirName(String distsDirName);

    /**
     * The name for the libs directory. This in interpreted relative to the project' build directory.
     */
    public abstract String getLibsDirName();

    public abstract void setLibsDirName(String libsDirName);

    /**
     * The base name to use for archive files.
     */
    public abstract String getArchivesBaseName();

    public abstract void setArchivesBaseName(String archivesBaseName);
}
