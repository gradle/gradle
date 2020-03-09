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

package org.gradle.internal.vfs;

import java.io.File;

/**
 * A {@link VirtualFileSystem} that can be instructed to try to maintain its
 * contents by watching the file system.
 */
public interface WatchingAwareVirtualFileSystem extends VirtualFileSystem {

    /**
     * Called when the build is started and watching is disabled for the current build.
     *
     * This means that watchers should be teared down and no
     * VFS state should be retained.
     */
    void watchingDisabledForCurrentBuild();

    /**
     * Called when the build is started and watching is enabled for the current build.
     */
    void afterStartingBuildWithWatchingEnabled();

    /**
     * Called when the build is completed and watching is enabled for the current build.
     */
    void beforeCompletingBuildWithWatchingEnabled(File rootProjectDir);
}
