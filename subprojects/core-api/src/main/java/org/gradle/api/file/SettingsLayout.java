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

/**
 * Provides access to important locations for a {@link Settings} instance.
 *
 * <p>An instance of this type can be injected into a plugin or other object by annotating a public constructor or method with {@code javax.inject.Inject}. It is also available via {@link Settings#getLayout()}.
 *
 * @since 8.5
 */
@Incubating
public interface SettingsLayout extends FileSystemLayout {
    /**
     * Returns the settings directory.
     *
     * @see Settings#getSettingsDir()
     */
    Directory getSettingsDirectory();

    /**
     * Returns the root directory of the build.
     *
     * @see Settings#getRootDir()
     */
    Directory getRootDirectory();
}
