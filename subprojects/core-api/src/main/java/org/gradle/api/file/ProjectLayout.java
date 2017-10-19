/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Provides access to several important locations for a project.
 *
 * An instance of the factory can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}. It is also available via {@link org.gradle.api.Project#getLayout()}.
 *
 * @since 4.1
 */
@Incubating
public interface ProjectLayout {
    /**
     * Returns the project directory.
     */
    Directory getProjectDirectory();

    /**
     * Returns the build directory for the project.
     */
    DirectoryProperty getBuildDirectory();

    /**
     * Creates a new {@link DirectoryVar} that uses the project directory to resolve paths, if required. The var has no initial value.
     *
     * @deprecated Use {@link #directoryProperty()} instead.
     */
    @Deprecated
    DirectoryVar newDirectoryVar();

    /**
     * Creates a new {@link DirectoryProperty} that uses the project directory to resolve paths, if required. The property has no initial value.
     *
     * @since 4.3
     */
    DirectoryProperty directoryProperty();

    /**
     * Creates a new {@link DirectoryProperty} that uses the project directory to resolve paths, if required. The property has the initial provider specified.
     *
     * @param initialProvider initial provider for the property
     * @since 4.4
     */
    DirectoryProperty directoryProperty(Provider<? extends Directory> initialProvider);

    /**
     * Creates a new {@link RegularFileVar} that uses the project directory to resolve paths, if required. The var has no initial value.
     *
     * @deprecated Use {@link #fileProperty()} instead.
     */
    @Deprecated
    RegularFileVar newFileVar();

    /**
     * Creates a new {@link RegularFileProperty} that uses the project directory to resolve paths, if required. The property has no initial value.
     *
     * @since 4.3
     */
    RegularFileProperty fileProperty();

    /**
     * Creates a new {@link RegularFileProperty} that uses the project directory to resolve paths, if required. The property has the initial provider specified.
     *
     * @param initialProvider initial provider for the property
     * @since 4.4
     */
    RegularFileProperty fileProperty(Provider<? extends RegularFile> initialProvider);

    /**
     * Creates a {@link RegularFile} provider whose location is calculated from the given {@link Provider}.
     */
    Provider<RegularFile> file(Provider<File> file);
}
