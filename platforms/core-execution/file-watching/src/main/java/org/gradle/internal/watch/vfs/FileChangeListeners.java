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

package org.gradle.internal.watch.vfs;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.watch.registry.FileWatcherRegistry;

import java.nio.file.Path;

/**
 * Allows the registration of {@link FileChangeListener}s.
 *
 * We can't use listener manager directly, since the listeners will be created in
 * child scopes of the user home scope.
 */
@ServiceScope(Scopes.UserHome.class)
public interface FileChangeListeners {
    /**
     * Registers the listener with the build, the listener can be unregistered with {@link #removeListener(FileChangeListener)}.
     */
    void addListener(FileChangeListener listener);

    void removeListener(FileChangeListener listener);

    void broadcastChange(FileWatcherRegistry.Type type, Path path);

    void broadcastWatchingError();
}
