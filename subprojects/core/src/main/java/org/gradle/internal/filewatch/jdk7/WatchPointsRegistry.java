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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.file.ImmutableDirectoryTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;

class WatchPointsRegistry {
    private final static Logger LOG = Logging.getLogger(WatchPointsRegistry.class);
    private final CombinedRootSubset rootSubset = new CombinedRootSubset();
    private ImmutableSet<? extends File> allRequestedRoots;
    private final boolean createNewStartingPointsUnderExistingRoots;
    private final FileSystem fileSystem;

    public WatchPointsRegistry(boolean createNewStartingPointsUnderExistingRoots, FileSystem fileSystem) {
        this.createNewStartingPointsUnderExistingRoots = createNewStartingPointsUnderExistingRoots;
        this.fileSystem = fileSystem;
        allRequestedRoots = ImmutableSet.of();
    }

    public Delta appendFileSystemSubset(FileSystemSubset fileSystemSubset, Iterable<? extends File> currentWatchPoints) {
        return new Delta(fileSystemSubset, ImmutableSet.copyOf(currentWatchPoints));
    }

    public boolean shouldFire(File file) {
        return rootSubset.contains(file);
    }

    public boolean shouldWatch(File directory) {
        final boolean result = rootSubset.isInRootsOrAncestorOrAnyRoot(directory) || isAncestorOfAnyRoot(directory, allRequestedRoots, true);
        if (!result && LOG.isDebugEnabled()) {
            LOG.debug("not watching directory: {} allRequestedRoots: {} roots: {} unfiltered: {}", directory, allRequestedRoots, rootSubset.roots, rootSubset.combinedFileSystemSubset);
        }
        return result;
    }

    class Delta {
        private FileSystemSubset fileSystemSubset;
        private Iterable<? extends File> roots;
        private FileSystemSubset combinedRoots;
        private Iterable<? extends File> startingWatchPoints;
        private ImmutableSet<? extends File> currentWatchPoints;

        private Delta(FileSystemSubset fileSystemSubset, ImmutableSet<? extends File> currentWatchPoints) {
            this.fileSystemSubset = fileSystemSubset;
            this.currentWatchPoints = currentWatchPoints;
            init();
        }

        private Delta init() {
            roots = fileSystemSubset.getRoots();
            combinedRoots = fileSystemSubset.unfiltered();
            Iterable<? extends File> startingWatchPointCandidates = calculateStartingWatchPoints(roots, combinedRoots);
            if (!currentWatchPoints.isEmpty()) {
                if (createNewStartingPointsUnderExistingRoots) {
                    startingWatchPoints = filterCurrentWatchPoints(startingWatchPointCandidates);
                    currentWatchPoints = ImmutableSet.<File>builder().addAll(currentWatchPoints).addAll(startingWatchPoints).build();
                } else {
                    Iterable<? extends File> combinedRoots = FileUtils.calculateRoots(Iterables.concat(currentWatchPoints, startingWatchPointCandidates));
                    startingWatchPoints = filterCurrentWatchPoints(combinedRoots);
                    currentWatchPoints = ImmutableSet.copyOf(combinedRoots);
                }
                rootSubset.append(fileSystemSubset);
            } else {
                startingWatchPoints = startingWatchPointCandidates;
                rootSubset.append(fileSystemSubset);
                currentWatchPoints = ImmutableSet.copyOf(startingWatchPoints);
            }
            allRequestedRoots = ImmutableSet.<File>builder().addAll(allRequestedRoots).addAll(roots).build();
            return this;
        }

        private ImmutableSet<File> filterCurrentWatchPoints(Iterable<? extends File> startingWatchPointCandidates) {
            final ImmutableSet.Builder<File> newStartingPoints = ImmutableSet.builder();
            for (File file : startingWatchPointCandidates) {
                if (!allRequestedRoots.contains(file) || !currentWatchPoints.contains(file)) {
                    newStartingPoints.add(file);
                }
            }
            return newStartingPoints.build();
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
                    return inCombinedRootsOrAncestorOfAnyRoot(input, roots, unfiltered);
                }
            });
        }

        private boolean inCombinedRootsOrAncestorOfAnyRootThis(File file) {
            return inCombinedRootsOrAncestorOfAnyRoot(file, roots, combinedRoots);
        }

        public Iterable<? extends File> getStartingWatchPoints() {
            return startingWatchPoints;
        }

        public boolean shouldWatch(File file) {
            boolean result = inCombinedRootsOrAncestorOfAnyRootThis(file) || isAncestorOfAnyRoot(file, allRequestedRoots);
            if (!result) {
                LOG.debug("not watching file: {} currentWatchPoints: {} allRequestedRoots: {} roots: {} unfiltered: {}", file, currentWatchPoints, allRequestedRoots, roots, combinedRoots);
            }
            return result;
        }
    }

    static private boolean inCombinedRootsOrAncestorOfAnyRoot(File file, Iterable<? extends File> roots, FileSystemSubset combinedRootsSubset) {
        return combinedRootsSubset.contains(file) || isAncestorOfAnyRoot(file, roots, true);
    }

    static private boolean isAncestorOfAnyRoot(File file, Iterable<? extends File> roots) {
        return isAncestorOfAnyRoot(file, roots, false);
    }

    static private boolean isAncestorOfAnyRoot(File file, Iterable<? extends File> roots, boolean acceptItSelf) {
        String absolutePathWithSeparator = file.getAbsolutePath() + File.separator;
        for (File root : roots) {
            if ((acceptItSelf && root.equals(file)) || root.getAbsolutePath().startsWith(absolutePathWithSeparator)) {
                return true;
            }
        }
        return false;
    }

    private static class CombinedRootSubset {
        private FileSystemSubset combinedFileSystemSubset;
        private Iterable<? extends File> roots;
        private FileSystemSubset unfiltered;

        public CombinedRootSubset() {
            combinedFileSystemSubset = FileSystemSubset.builder().build();
            updateRoots();
        }

        public void append(FileSystemSubset fileSystemSubset) {
            combinedFileSystemSubset = FileSystemSubset.builder().add(combinedFileSystemSubset).add(fileSystemSubset).build();
            updateRoots();
        }

        private void updateRoots() {
            roots = combinedFileSystemSubset.getRoots();
            unfiltered = new FileSystemSubset(ImmutableList.copyOf(roots), ImmutableList.<ImmutableDirectoryTree>of());
        }

        public boolean isInRootsOrAncestorOrAnyRoot(File directory) {
            return inCombinedRootsOrAncestorOfAnyRoot(directory, roots, unfiltered);
        }

        public boolean contains(File file) {
            return combinedFileSystemSubset.contains(file);
        }
    }
}
