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
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                    if (inUnfilteredSubsetOrAncestorOfAnyRoot(path.toFile())) {
                        watchDir(path);
                        return FileVisitResult.CONTINUE;
                    } else {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
            });
        }
    }

    private void watchDir(Path dir) throws IOException {
        try {
            dir.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
        } catch (IOException e) {
            // Windows at least will sometimes throw odd exceptions like java.nio.file.AccessDeniedException
            // if the file gets deleted while the watch is being set up.
            // So, we just ignore the exception if the dir doesn't exist anymore
            if (Files.exists(dir)) {
                throw e;
            }
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
        maybeFire(watcher, event);

        if (watcher.isRunning() && file.isDirectory() && event.getType().equals(FileWatcherEvent.Type.CREATE)) {
            try {
                newDirectory(watcher, file);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private void maybeFire(FileWatcher watcher, FileWatcherEvent event) {
        if (fileSystemSubset.contains(event.getFile())) {
            delegate.onChange(watcher, event);
        }
    }

    private void newDirectory(FileWatcher watcher, File dir) throws IOException {
        if (!watcher.isRunning()) {
            return;
        }
        if (dir.exists()) {
            watchDir(dir.toPath());
            File[] contents = dir.listFiles();
            if (contents != null) {
                for (File file : contents) {
                    maybeFire(watcher, FileWatcherEvent.create(file));
                    if (!watcher.isRunning()) {
                        return;
                    }

                    if (file.isDirectory()) {
                        newDirectory(watcher, file);
                    }
                }
            }
        }
    }
}

