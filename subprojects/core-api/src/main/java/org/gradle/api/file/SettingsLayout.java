/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.file;

import org.gradle.api.Incubating;
import org.gradle.api.initialization.Settings;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Provides access to important locations for a {@link Settings} instance.
 * <p>
 * File system locations (or providers thereof) based on relative paths will be
 * resolved against this layout's reference location, as defined by {@link #getSettingsDirectory()}.
 * <p>
 * An instance of this type can be injected into a plugin or other object by
 * annotating a public constructor or method with {@code javax.inject.Inject}.
 * It is also available via {@link Settings#getLayout()}.
 *
 * @since 8.5
 */
@Incubating
@ServiceScope(Scopes.Build.class)
public interface SettingsLayout extends FileSystemLayout {
    /**
     * Returns the settings directory.
     * <p>
     * The settings directory is the directory containing the settings file.
     * </p>
     *
     * @see Settings#getSettingsDir()
     */
    Directory getSettingsDirectory();

    /**
     * Returns the root directory of the build.
     * <p>
     * The root directory is the project directory of the root project.
     * </p>
     *
     * @see Settings#getRootDir()
     */
    Directory getRootDirectory();


    /**
     * {@inheritDoc}
     *
     * <p>
     * File system locations based on relative paths will be
     * resolved against this layout's reference location, as defined by {@link #getSettingsDirectory()}.
     * </p>
     */
    @Override
    Provider<RegularFile> file(Provider<File> file);

    /**
     * {@inheritDoc}
     *
     * <p>
     * File system locations based on relative paths will be
     * resolved against this layout's reference location, as defined by {@link #getSettingsDirectory()}.
     * </p>
     */
    @Override
    Provider<Directory> dir(Provider<File> file);
}
