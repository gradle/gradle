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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.FileUtils;

import java.io.File;

class WatchPointsRegistry {
    private FileSystemSubset combinedFileSystemSubset;
    private ImmutableCollection<? extends File> currentRoots;

    WatchPointsRegistry() {
        combinedFileSystemSubset = FileSystemSubset.builder().build();
        currentRoots = ImmutableSet.of();
    }

    public Delta appendFileSystemSubset(FileSystemSubset fileSystemSubset) {
        return new Delta(fileSystemSubset);
    }

    public boolean shouldFire(File file) {
        return combinedFileSystemSubset.contains(file);
    }

    class Delta {
        private FileSystemSubset fileSystemSubset;
        private Iterable<? extends File> roots;
        private FileSystemSubset unfiltered;
        private Iterable<? extends File> startingWatchPoints;

        private Delta(FileSystemSubset fileSystemSubset) {
            this.fileSystemSubset = fileSystemSubset;
            init();
        }

        private Delta init() {
            roots = fileSystemSubset.getRoots();
            unfiltered = fileSystemSubset.unfiltered();

            startingWatchPoints = calculateStartingWatchPoints(roots, unfiltered);
            if (!currentRoots.isEmpty()) {
                final ImmutableSet.Builder<File> newStartingPoints = ImmutableSet.builder();
                Iterable<? extends File> combinedRoots = FileUtils.calculateRoots(Iterables.concat(currentRoots, startingWatchPoints));
                for (File file : combinedRoots) {
                    if (!currentRoots.contains(file)) {
                        newStartingPoints.add(file);
                    }
                }
                startingWatchPoints = newStartingPoints.build();
                currentRoots = ImmutableSet.copyOf(combinedRoots);
                combinedFileSystemSubset = FileSystemSubset.builder().add(combinedFileSystemSubset).add(fileSystemSubset).build();
            } else {
                currentRoots = ImmutableSet.copyOf(startingWatchPoints);
                combinedFileSystemSubset = fileSystemSubset;
            }
            return this;
        }

        private Iterable<? extends File> calculateStartingWatchPoints(final Iterable<? extends File> roots, final FileSystemSubset unfiltered) {
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
            return Iterables.filter(FileUtils.calculateRoots(enclosingDirsThatExist), new Predicate<File>() {
                @Override
                public boolean apply(File input) {
                    return inUnfilteredSubsetOrAncestorOfAnyRoot(input, roots, unfiltered);
                }
            });
        }

        private boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file, Iterable<? extends File> roots, FileSystemSubset unfilteredFileSystemSubset) {
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

        public boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file) {
            return inUnfilteredSubsetOrAncestorOfAnyRoot(file, roots, unfiltered);
        }

        public Iterable<? extends File> getStartingWatchPoints() {
            return startingWatchPoints;
        }
    }
}
