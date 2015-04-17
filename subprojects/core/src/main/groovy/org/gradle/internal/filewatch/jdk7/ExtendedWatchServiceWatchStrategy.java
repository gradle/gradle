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

import com.sun.nio.file.ExtendedWatchEventModifier;
import org.gradle.internal.os.OperatingSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;

/**
 * On Windows, the Java WatchService supports watching a full sub tree.
 * On other OSs, you can only watch a single directory at a time.
 *
 * {@see ExtendedDirTreeWatchRegistry} for a use case
 */
class ExtendedWatchServiceWatchStrategy extends WatchServiceWatchStrategy implements FileTreeWatchStrategy {
    static final WatchEvent.Modifier[] EXTENDED_WATCH_MODIFIERS = new WatchEvent.Modifier[]{ExtendedWatchEventModifier.FILE_TREE};

    ExtendedWatchServiceWatchStrategy(WatchService watchService) {
        super(watchService);
    }

    // ExtendedWatchEventModifier.FILE_TREE is only supported on Windows
    public static boolean isSupported() {
        return OperatingSystem.current().isWindows();
    }

    @Override
    public void watchFileTree(Path path) throws IOException {
        path.register(watchService, WATCH_KINDS, EXTENDED_WATCH_MODIFIERS);
    }
}
