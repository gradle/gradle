/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.vfs.FileChangeListener;
import org.gradle.internal.watch.vfs.FileChangeListeners;

import java.nio.file.Path;

public class DefaultFileChangeListeners implements FileChangeListeners {
    private final AnonymousListenerBroadcast<FileChangeListener> broadcaster;

    public DefaultFileChangeListeners(ListenerManager listenerManager) {
        this.broadcaster = listenerManager.createAnonymousBroadcaster(FileChangeListener.class);
    }

    @Override
    public void addListener(FileChangeListener listener) {
        broadcaster.add(listener);
    }

    @Override
    public void removeListener(FileChangeListener listener) {
        broadcaster.remove(listener);
    }

    @Override
    public void broadcastChange(FileWatcherRegistry.Type type, Path path) {
        broadcaster.getSource().handleChange(type, path);
    }

    @Override
    public void broadcastWatchingError() {
        broadcaster.getSource().stopWatchingAfterError();
    }
}
