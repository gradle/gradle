/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;


/**
 * Operations on the file system.
 *
 * <p>An instance of this type can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 *
 * @since 6.0
 */
@ServiceScope(Scopes.Build.class)
public interface FileSystemOperations {

    /**
     * Copies the specified files.
     * The given action is used to configure a {@link CopySpec}, which is then used to copy the files.
     *
     * @param action Action to configure the CopySpec
     * @return {@link WorkResult} that can be used to check if the copy did any work.
     */
    WorkResult copy(Action<? super CopySpec> action);

    /**
     * Synchronizes the contents of a destination directory with some source directories and files.
     * The given action is used to configure a {@link CopySpec}, which is then used to synchronize the files.
     *
     * @param action action Action to configure the CopySpec.
     * @return {@link WorkResult} that can be used to check if the sync did any work.
     */
    WorkResult sync(Action<? super CopySpec> action);

    /**
     * Deletes the specified files.
     * The given action is used to configure a {@link DeleteSpec}, which is then used to delete the files.
     *
     * @param action Action to configure the DeleteSpec
     * @return {@link WorkResult} that can be used to check if delete did any work.
     */
    WorkResult delete(Action<? super DeleteSpec> action);

    /**
     * Creates and configures file access permissions. Differs from directory permissions due to
     * the default value the permissions start out with before the configuration is applied.
     * For details see {@link ConfigurableFilePermissions}.
     *
     * @param configureAction The configuration that gets applied to the newly created {@code FilePermissions}.
     *
     * @since 8.3
     */
    @Incubating
    ConfigurableFilePermissions filePermissions(Action<? super ConfigurableFilePermissions> configureAction);

    /**
     * Creates and configures directory access permissions. Differs from file permissions due to
     * the default value the permissions start out with before the configuration is applied.
     * For details see {@link ConfigurableFilePermissions}.
     *
     * @param configureAction The configuration that gets applied to the newly created {@code FilePermissions}.
     *
     * @since 8.3
     */
    @Incubating
    ConfigurableFilePermissions directoryPermissions(Action<? super ConfigurableFilePermissions> configureAction);

    /**
     * Creates file/directory access permissions and initializes them via a Unix style permission string.
     * For details see {@link ConfigurableFilePermissions#unix(String)}.
     * <p>
     * Doesn't have separate variants for files and directories, like other configuration methods,
     * because the Unix style permission input completely overwrites the default values, so
     * the distinction doesn't matter.
     *
     * @since 8.3
     */
    @Incubating
    ConfigurableFilePermissions permissions(String unixNumericOrSymbolic);

    /**
     * Creates file/directory access permissions and initializes them via a Unix style numeric permissions.
     * For details see {@link ConfigurableFilePermissions#unix(int)}.
     * <p>
     * Doesn't have separate variants for files and directories, like other configuration methods,
     * because the Unix style permission input completely overwrites the default values, so
     * the distinction doesn't matter.
     *
     * @since 8.3
     */
    @Incubating
    ConfigurableFilePermissions permissions(int unixNumeric);

    /**
     * {@link Provider} based version of {@link #permissions(String)},  to facilitate wiring into property chains.
     *
     * @since 8.3
     */
    @Incubating
    Provider<ConfigurableFilePermissions> permissions(Provider<String> permissions);
}
