/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.watch.vfs.WatchingAwareVirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A {@link org.gradle.internal.vfs.VirtualFileSystem} which is not able to register any watches.
 */
public class NonWatchingVirtualFileSystem extends AbstractDelegatingVirtualFileSystem implements WatchingAwareVirtualFileSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(NonWatchingVirtualFileSystem.class);

    public NonWatchingVirtualFileSystem(AbstractVirtualFileSystem delegate) {
        super(delegate);
    }

    @Override
    public void afterBuildStarted(boolean watchingEnabled) {
        if (watchingEnabled) {
            LOGGER.warn("Watching the file system is not supported on this operating system.");
        }
        invalidateAll();
    }

    @Override
    public void buildRootDirectoryAdded(File buildRootDirectory) {
    }

    @Override
    public void beforeBuildFinished(boolean watchingEnabled) {
        invalidateAll();
    }
}
