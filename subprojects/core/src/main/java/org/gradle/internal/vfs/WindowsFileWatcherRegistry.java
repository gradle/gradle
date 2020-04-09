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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;

public class WindowsFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {

    private static final int BUFFER_SIZE = 128 * 1024;

    public WindowsFileWatcherRegistry(ChangeHandler handler) {
        super(
            eventQueue -> Native.get(WindowsFileEventFunctions.class)
                .newWatcher(eventQueue)
                .withBufferSize(BUFFER_SIZE)
                .start(),
            handler,
            HierarchicalFileWatcherUpdater::new
        );
    }

    public static class Factory implements FileWatcherRegistryFactory {

        @Override
        public FileWatcherRegistry startWatcher(ChangeHandler handler) {
            return new WindowsFileWatcherRegistry(handler);
        }
    }
}
