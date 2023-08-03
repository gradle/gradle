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

package org.gradle.api.plugins;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * An extension used for {@link BasePlugin}.
 * <p>
 * Replaces {@link BasePluginConvention}.
 *
 * @since 7.1
 */
public interface BasePluginExtension {
    /**
     * Returns the directory to generate TAR and ZIP archives into.
     *
     * @return The directory. Never returns null.
     */
    DirectoryProperty getDistsDirectory();

    /**
     * Returns the directory to generate JAR and WAR archives into.
     *
     * @return The directory. Never returns null.
     */
    DirectoryProperty getLibsDirectory();

    /**
     * The base name to use for archive files.
     */
    Property<String> getArchivesName();

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getDistsDirectory()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    String getDistsDirName();

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getDistsDirectory()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    void setDistsDirName(String distsDirName);

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getLibsDirectory()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    String getLibsDirName();

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getLibsDirectory()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    void setLibsDirName(String libsDirName);

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getArchivesName()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    String getArchivesBaseName();

    /**
     * This method is only here to maintain compatibility with existing builds.
     *
     * @deprecated Use {@link #getArchivesName()}. This method is scheduled for removal in Gradle 9.0.
     */
    @Deprecated
    void setArchivesBaseName(String archivesBaseName);
}
