/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.jspecify.annotations.NullMarked;

import java.io.File;

@NullMarked
public interface EclipseClasspathInternal {
    /**
     * The configurations whose files are to be added as classpath entries.
     * <p>
     * See {@link EclipseClasspath} for an example.
     *
     * @return the configurations whose files are to be added as classpath entries
     */
    ListProperty<Configuration> getPlusConfigurationsProperty();

    /**
     * The default output directory where Eclipse puts compiled classes.
     * @return the default output directory
     */
    RegularFileProperty getDefaultOutputDirProperty();

    /**
     * The class folders to be added.
     * @return the class folders to be added
     */
    ListProperty<File> getClassFoldersProperty();
}
