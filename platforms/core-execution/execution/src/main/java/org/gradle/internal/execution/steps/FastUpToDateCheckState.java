/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scope.UserHome.class)
public class FastUpToDateCheckState implements FastUpToDateCheckLifecycle {
    static final char SEP = File.separatorChar;

    private final Set<String> changedPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> producedOutputRootPaths = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedIdentity> cachedIdentities = new ConcurrentHashMap<>();
    private volatile boolean configurationCacheHit;
    // The CC cache key of the most recent CC-hit build. Retained across builds (including CC-miss
    // builds) so the next CC hit can compare against it and drop cached identities if the key
    // changed — keeps memory scoped to a single CC configuration in a long-lived daemon.
    @Nullable
    private volatile String lastSeenCacheKey;
    private volatile boolean watchingFileSystem;
    private volatile boolean watchingError;
    // True only after at least one build has completed with watching active.
    // On a cold daemon, changedPaths is empty but that means "not yet watching",
    // not "nothing changed". This flag ensures we only fast-path after the VFS
    // has established a baseline from a completed build.
    private volatile boolean hasVfsBaseline;

    public void recordChange(Path path) {
        changedPaths.add(path.toString());
    }

    public void stopWatchingAfterError() {
        watchingError = true;
        // Cached identities rely on VFS to detect changes to their input roots.
        // Once watching is compromised, any entry could be stale — drop them all
        // so the next build re-populates from fresh fingerprints.
        cachedIdentities.clear();
    }

    public void recordProducedOutputRoot(String outputRootPath) {
        producedOutputRootPaths.add(outputRootPath);
    }

    public boolean isFastPathEnabled() {
        return configurationCacheHit && watchingFileSystem && !watchingError && hasVfsBaseline;
    }

    /**
     * Returns the set of file paths changed since the last build, as reported by VFS file watching.
     * The returned set is a live concurrent view — safe to iterate from multiple threads.
     */
    Set<String> getChangedPaths() {
        return changedPaths;
    }

    /**
     * Returns the set of output root paths produced by tasks that executed in the current build.
     * The returned set is a live concurrent view — safe to iterate from multiple threads.
     */
    Set<String> getProducedOutputRootPaths() {
        return producedOutputRootPaths;
    }

    /**
     * Looks up a cached identity for a unit of work with the given stable cache key.
     * Returns {@code null} if the fast path is not enabled, there is no cached entry,
     * or any of the cached input root paths overlap with VFS-reported changes.
     */
    @Nullable
    public CachedIdentity lookupCachedIdentity(String stableKey) {
        if (!isFastPathEnabled()) {
            return null;
        }
        CachedIdentity cached = cachedIdentities.get(stableKey);
        if (cached == null) {
            return null;
        }
        if (hasVfsChangesOverlappingWith(cached.getInputRootPaths())) {
            return null;
        }
        return cached;
    }

    /**
     * Stores a cached identity under the given stable cache key. Used by IdentifyStep
     * after computing a fresh identity, so subsequent builds can reuse it.
     */
    public void storeCachedIdentity(String stableKey, CachedIdentity identity) {
        cachedIdentities.put(stableKey, identity);
    }

    private boolean hasVfsChangesOverlappingWith(Set<String> rootPaths) {
        if (changedPaths.isEmpty() || rootPaths.isEmpty()) {
            return false;
        }
        for (String changedPath : changedPaths) {
            for (String rootPath : rootPaths) {
                if (pathsOverlap(changedPath, rootPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean pathsOverlap(String path1, String path2) {
        if (path1.equals(path2)) {
            return true;
        }
        if (path2.length() > path1.length()
            && path2.charAt(path1.length()) == SEP
            && path2.startsWith(path1)) {
            return true;
        }
        if (path1.length() > path2.length()
            && path1.charAt(path2.length()) == SEP
            && path1.startsWith(path2)) {
            return true;
        }
        return false;
    }

    @Override
    public void setConfigurationCacheHit(@Nullable String cacheKey) {
        this.configurationCacheHit = cacheKey != null;
        if (!Objects.equals(cacheKey, lastSeenCacheKey)) {
            // Any CC state transition (hit→miss, miss→hit, or hit with a different key)
            // drops the identity cache: a CC miss means configuration changed, and a
            // different hit key may mean a different build graph. Both are natural points
            // to release memory accumulated during the previous CC configuration.
            cachedIdentities.clear();
            lastSeenCacheKey = cacheKey;
        }
    }

    public void setWatchingFileSystem(boolean watchingFileSystem) {
        this.watchingFileSystem = watchingFileSystem;
    }

    @Override
    public void resetForBuildStart() {
        // Before dropping the previous build's produced output paths, invalidate any cached
        // identities whose inputs overlap with them. VFS changedPaths is wiped at build end,
        // so a task that modified a file in the last build would not appear in VFS changes
        // anymore — we use producedOutputRootPaths to catch that case and drop stale entries.
        invalidateCachedIdentitiesOverlappingWith(producedOutputRootPaths);
        producedOutputRootPaths.clear();
        configurationCacheHit = false;
        watchingError = false;
    }

    private void invalidateCachedIdentitiesOverlappingWith(Set<String> producedPaths) {
        if (producedPaths.isEmpty() || cachedIdentities.isEmpty()) {
            return;
        }
        List<String> toInvalidate = new ArrayList<>();
        for (Map.Entry<String, CachedIdentity> entry : cachedIdentities.entrySet()) {
            if (anyPathOverlaps(producedPaths, entry.getValue().getInputRootPaths())) {
                toInvalidate.add(entry.getKey());
            }
        }
        for (String key : toInvalidate) {
            cachedIdentities.remove(key);
        }
    }

    private static boolean anyPathOverlaps(Set<String> producedPaths, Set<String> inputRootPaths) {
        for (String producedPath : producedPaths) {
            for (String inputRoot : inputRootPaths) {
                if (pathsOverlap(producedPath, inputRoot)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void clearChangedPaths() {
        changedPaths.clear();
        hasVfsBaseline = true;
    }

    /**
     * Holds the data needed to reconstruct an {@link IdentityContext} without re-fingerprinting inputs.
     * The set of input root paths is precomputed so VFS-change checks are cheap on the fast path.
     */
    public static final class CachedIdentity {
        private final ImplementationSnapshot implementation;
        private final ImmutableList<ImplementationSnapshot> additionalImplementations;
        private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;
        private final Identity identity;
        private final Set<String> inputRootPaths;

        public CachedIdentity(
            ImplementationSnapshot implementation,
            ImmutableList<ImplementationSnapshot> additionalImplementations,
            ImmutableSortedMap<String, ValueSnapshot> inputProperties,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
            Identity identity
        ) {
            this.implementation = implementation;
            this.additionalImplementations = additionalImplementations;
            this.inputProperties = inputProperties;
            this.inputFileProperties = inputFileProperties;
            this.identity = identity;
            this.inputRootPaths = collectRootPaths(inputFileProperties);
        }

        private static Set<String> collectRootPaths(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties) {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            for (CurrentFileCollectionFingerprint fp : inputFileProperties.values()) {
                builder.addAll(fp.getRootHashes().keySet());
            }
            return builder.build();
        }

        public ImplementationSnapshot getImplementation() {
            return implementation;
        }

        public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
            return additionalImplementations;
        }

        public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
            return inputProperties;
        }

        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
            return inputFileProperties;
        }

        public Identity getIdentity() {
            return identity;
        }

        Set<String> getInputRootPaths() {
            return inputRootPaths;
        }
    }
}
