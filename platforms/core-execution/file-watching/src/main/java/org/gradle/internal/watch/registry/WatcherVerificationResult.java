/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.watch.registry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * What a scan of the retained virtual file system established about the hierarchies it walked.
 *
 * <p>The two parts carry different weight. An outdated path is an observation, so a scan cut short by
 * its deadline still reports the ones it found. A verified hierarchy is a claim that its whole retained
 * state agrees with the file system on file size, file modification time, and directory membership, so
 * only a completed walk contributes one.</p>
 */
public class WatcherVerificationResult {
    public static final WatcherVerificationResult EMPTY =
        new WatcherVerificationResult(ImmutableList.of(), ImmutableSet.of());

    private final List<String> outdatedPaths;
    private final Set<File> verifiedHierarchies;

    public WatcherVerificationResult(List<String> outdatedPaths, Set<File> verifiedHierarchies) {
        this.outdatedPaths = outdatedPaths;
        this.verifiedHierarchies = verifiedHierarchies;
    }

    /**
     * Locations whose retained snapshot no longer matches the file system.
     */
    public List<String> getOutdatedPaths() {
        return outdatedPaths;
    }

    /**
     * Hierarchies the scan walked to the end, which therefore need no watch probe event to be trusted
     * for this build.
     *
     * <p>The agreement covers file size, file modification time, and directory membership. It does not
     * cover file content: a rewrite that preserves both the size and the modification time is invisible
     * to the scan, and the retained content hash is then reused. Nor does it cover what the walk skips
     * — the probe files Gradle writes for its own signalling, and everything
     * {@code WatchableHierarchies.ignoredForWatching} covers, which is Gradle's immutable locations and
     * every snapshot reached through a symlink.</p>
     */
    public Set<File> getVerifiedHierarchies() {
        return verifiedHierarchies;
    }
}
