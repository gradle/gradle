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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;

import java.io.File;

class WatchPointsRegistry {
    private final static Logger LOG = Logging.getLogger(WatchPointsRegistry.class);
    private FileSystemSubset combinedFileSystemSubset;
    private ImmutableSet<? extends File> allRequestedRoots;
    private ImmutableCollection<? extends File> currentWatchPoints;
    private final boolean createNewStartingPointsUnderExistingRoots;

    public WatchPointsRegistry(boolean createNewStartingPointsUnderExistingRoots) {
        this.createNewStartingPointsUnderExistingRoots = createNewStartingPointsUnderExistingRoots;
        combinedFileSystemSubset = FileSystemSubset.builder().build();
        currentWatchPoints = ImmutableSet.of();
        allRequestedRoots = ImmutableSet.of();
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
            allRequestedRoots = ImmutableSet.<File>builder().addAll(allRequestedRoots).addAll(roots).build();
            unfiltered = fileSystemSubset.unfiltered();
            Iterable<? extends File> startingWatchPointCandidates = calculateStartingWatchPoints(roots, unfiltered);
            if (!currentWatchPoints.isEmpty()) {
                if (createNewStartingPointsUnderExistingRoots) {
                    startingWatchPoints = filterCurrentWatchPoints(startingWatchPointCandidates);
                    currentWatchPoints = ImmutableSet.<File>builder().addAll(currentWatchPoints).addAll(startingWatchPoints).build();
                } else {
                    Iterable<? extends File> combinedRoots = FileUtils.calculateRoots(Iterables.concat(currentWatchPoints, startingWatchPointCandidates));
                    startingWatchPoints = filterCurrentWatchPoints(combinedRoots);
                    currentWatchPoints = ImmutableSet.copyOf(combinedRoots);
                }
                combinedFileSystemSubset = FileSystemSubset.builder().add(combinedFileSystemSubset).add(fileSystemSubset).build();
            } else {
                startingWatchPoints = startingWatchPointCandidates;
                combinedFileSystemSubset = fileSystemSubset;
                currentWatchPoints = ImmutableSet.copyOf(startingWatchPoints);
            }
            return this;
        }

        private ImmutableSet<File> filterCurrentWatchPoints(Iterable<? extends File> startingWatchPointCandidates) {
            final ImmutableSet.Builder<File> newStartingPoints = ImmutableSet.builder();
            for (File file : startingWatchPointCandidates) {
                if (!currentWatchPoints.contains(file)) {
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
                    return inUnfilteredSubsetOrAncestorOfAnyRoot(input, roots, unfiltered);
                }
            });
        }

        private boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file, Iterable<? extends File> roots, FileSystemSubset unfilteredFileSystemSubset) {
            return unfilteredFileSystemSubset.contains(file) || isAncestorOfAnyRoot(file, roots, false);
        }

        private boolean isAncestorOfAnyRoot(File file, Iterable<? extends File> roots) {
            return isAncestorOfAnyRoot(file, roots, true);
        }

        private boolean isAncestorOfAnyRoot(File file, Iterable<? extends File> roots, boolean onlyAncestors) {
            String absolutePathWithSeparator = file.getAbsolutePath() + File.separator;
            for (File root : roots) {
                if ((!onlyAncestors && root.equals(file)) || root.getAbsolutePath().startsWith(absolutePathWithSeparator)) {
                    return true;
                }
            }
            return false;
        }

        private boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file) {
            return inUnfilteredSubsetOrAncestorOfAnyRoot(file, roots, unfiltered);
        }

        public Iterable<? extends File> getStartingWatchPoints() {
            return startingWatchPoints;
        }

        public boolean shouldWatch(File file) {
            boolean result = (inUnfilteredSubsetOrAncestorOfAnyRoot(file) || isAncestorOfAnyRoot(file, allRequestedRoots)) && !isAncestorOfAnyRoot(file, currentWatchPoints);
            if (!result) {
                LOG.debug("not watching file: {} currentWatchPoints: {} allRequestedRoots: {} roots: {} unfiltered: {}", file, currentWatchPoints, allRequestedRoots, roots, unfiltered);
            }
            return result;
        }
    }
}
