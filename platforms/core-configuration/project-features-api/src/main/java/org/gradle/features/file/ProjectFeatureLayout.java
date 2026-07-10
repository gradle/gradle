/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.file;

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import java.io.File;

/**
 * Provides access to several important locations for a project feature.
 *
 * <p>An instance of this type can be injected into an object by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 *
 * @since 9.5.0
 */
@Incubating
public interface ProjectFeatureLayout {
    /**
     * Returns the project directory.
     *
     * @since 9.5.0
     */
    Directory getProjectDirectory();

    /**
     * Returns the settings directory.
     * <p>
     * The settings directory is the directory containing the settings file.
     * It is shared by all projects in the build.
     *
     * @since 9.5.0
     */
    Directory getSettingsDirectory();

    /**
     * Returns a context-specific build directory for this feature binding.
     *
     * @since 9.5.0
     */
    Provider<Directory> getContextBuildDirectory();

    /**
     * Creates a {@link RegularFile} provider whose location is calculated from the given {@link Provider}.
     * <p>
     * File system locations based on relative paths will be
     * resolved against this layout's reference location, as defined by {@link #getProjectDirectory()}.
     *
     * @since 9.5.0
     */
    Provider<RegularFile> file(Provider<File> file);

    /**
     * Creates a {@link Directory} provider whose location is calculated from the given {@link Provider}.
     * <p>
     * File system locations based on relative paths will be
     * resolved against this layout's reference location, as defined by {@link #getProjectDirectory()}.
     *
     * @since 9.5.0
     */
    Provider<Directory> dir(Provider<File> file);
}
