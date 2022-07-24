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

package org.gradle.tooling.internal.protocol;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.UnsupportedVersionException;

import java.util.List;

/**
 * Mixed into a provider connection, to allow notifying the daemon about changed paths.
 *
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This method is used by all consumer versions from 6.1.</p>
 * <p>Provider compatibility: This method is implemented by all provider versions from 6.1.</p>
 *
 * @since 6.1
 * @see org.gradle.tooling.internal.protocol.ConnectionVersion4
 */
public interface InternalInvalidatableVirtualFileSystemConnection extends InternalProtocolInterface {

    /**
     * Notifies all daemons about file changes made by an external process, like an IDE.
     *
     * <p>The daemons will use this information to update the retained file system state.
     *
     * <p>The paths which are passed in need to be absolute, canonicalized paths.
     * For a delete, the deleted path should be passed.
     * For a rename, the old and the new path should be passed.
     * When creating a new file, the path to the file should be passed.
     *
     * <p>The call is synchronous, i.e. the method ensures that the changed paths are taken into account
     * by the daemon after it returned. This ensures that for every build started
     * after this method has been called knows about the changed paths.
     *
     * <p>If the version of Gradle does not support virtual file system retention (i.e. &lt; 6.1),
     * then the operation is a no-op.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 6.1.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 6.1.</p>
     *
     * @param changedPaths Absolute paths which have been changed by the external process.
     * @throws UnsupportedVersionException When the target Gradle version is &lt;= 2.5.
     * @throws GradleConnectionException On some other failure using the connection.
     * @since 6.1
     */
    void notifyDaemonsAboutChangedPaths(List<String> changedPaths, BuildParameters parameters);
}
