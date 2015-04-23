/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.internal.concurrent.Stoppable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

abstract class WatchRegistry<T> {
    private final WatchStrategy watchStrategy;
    private final Map<Path, Stoppable> watchHandles;

    WatchRegistry(WatchStrategy watchStrategy) {
        this.watchStrategy = watchStrategy;
        this.watchHandles = new HashMap<Path, Stoppable>();
    }

    abstract public void enterRegistrationMode();

    abstract public void exitRegistrationMode();

    abstract public void register(String sourceKey, Iterable<T> watchItems) throws IOException;

    abstract public void handleChange(ChangeDetails changeDetails, FileWatcherChangesNotifier changesNotifier);

    protected void watchDirectory(Path path) throws IOException {
        Stoppable stoppable = watchStrategy.watchSingleDirectory(path);
        watchHandles.put(path, stoppable);
    }

    protected boolean isWatching(Path path) {
        return watchHandles.containsKey(path);
    }

    protected void unwatchDirectory(Path path) {
        Stoppable stoppable = watchHandles.remove(path);
        if(stoppable != null) {
            stoppable.stop();
        }
    }

    // subclass hook for unit tests
    protected Path dirToPath(File dir) {
        return dir.getAbsoluteFile().toPath();
    }

    protected RelativePath toRelativePath(File file, Path path) {
        return RelativePath.parse(!file.isDirectory(), path.toString());
    }

    protected FileTreeElement toFileTreeElement(Path fullPath, Path relativePath) {
        File file = fullPath.toFile();
        return new CustomFileTreeElement(file, toRelativePath(file, relativePath));
    }

    private static class CustomFileTreeElement extends DefaultFileTreeElement {
        public CustomFileTreeElement(File file, RelativePath relativePath) {
            super(file, relativePath, null, null);
        }
    }
}
