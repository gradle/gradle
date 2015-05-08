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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.FileUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

class WatchServiceRegistrar implements FileWatcherListener {
    // http://stackoverflow.com/a/18362404
    // make watch sensitivity as 2 seconds on MacOSX, polls every 2 seconds for changes. Default is 10 seconds.
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS = new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};

    private final WatchService watchService;
    private final FileSystemSubset fileSystemSubset;
    private final FileSystemSubset unfilteredFileSystemSubset;
    private final FileWatcherListener delegate;
    private final Iterable<? extends File> roots;

    WatchServiceRegistrar(WatchService watchService, FileSystemSubset fileSystemSubset, FileWatcherListener delegate) throws IOException {
        this.watchService = watchService;
        this.fileSystemSubset = fileSystemSubset;
        this.unfilteredFileSystemSubset = fileSystemSubset.unfiltered();
        this.roots = fileSystemSubset.getRoots();
        this.delegate = delegate;

        // Turn the requested watch points into actual enclosing directories that exist
        Iterable<File> enclosingDirsThatExist = Iterables.transform(roots, new Function<File, File>() {
            @Override
            public File apply(File input) {
                File target = input;
                while (!target.isDirectory()) {
                    target = target.getParentFile();
                }
                return target;
            }
        });

        // Collapse the set
        Iterable<? extends File> startingWatchPoints = FileUtils.calculateRoots(enclosingDirsThatExist);

        for (File dir : startingWatchPoints) {
            maybeWatchDirRecurse(dir);
        }
    }

    private void maybeWatchDirRecurse(File dir) throws IOException {
        if (dir.isDirectory()) {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (inUnfilteredSubsetOrAncestorOfAnyRoot(dir.toFile())) {
                        dir.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
            });
        }
    }

    private boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file) {
        if (unfilteredFileSystemSubset.contains(file)) {
            return true;
        } else {
            String absolutePathWithSeparator = file.getAbsolutePath() + File.separator;
            for (File root : roots) {
                if (root.equals(file) || root.getPath().startsWith(absolutePathWithSeparator)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onChange(FileWatcher watcher, FileWatcherEvent event) {
        if (event.getType().equals(FileWatcherEvent.Type.UNDEFINED) || event.getFile() == null) {
            delegate.onChange(watcher, event);
            return;
        }

        File file = event.getFile();
        if (fileSystemSubset.contains(file)) {
            delegate.onChange(watcher, event);
        }

        if (watcher.isRunning() && file.isDirectory() && event.getType().equals(FileWatcherEvent.Type.CREATE)) {
            try {
                maybeWatchDirRecurse(file);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

}
