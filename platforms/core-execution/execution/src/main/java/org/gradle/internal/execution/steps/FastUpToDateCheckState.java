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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scope.UserHome.class)
public class FastUpToDateCheckState implements FastUpToDateCheckLifecycle {
    private final Set<String> changedPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> producedOutputRootPaths = ConcurrentHashMap.newKeySet();
    private volatile boolean configurationCacheHit;
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
    }

    public void recordProducedOutputRoots(Set<String> outputRootPaths) {
        producedOutputRootPaths.addAll(outputRootPaths);
    }

    public boolean isFastPathEnabled() {
        return configurationCacheHit && watchingFileSystem && !watchingError && hasVfsBaseline;
    }

    public boolean hasVfsChangesOverlappingWith(Set<String> rootPaths) {
        if (changedPaths.isEmpty()) {
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

    public boolean hasProducedOutputsOverlappingWith(Set<String> inputRootPaths) {
        if (producedOutputRootPaths.isEmpty()) {
            return false;
        }
        for (String producedRoot : producedOutputRootPaths) {
            for (String inputRoot : inputRootPaths) {
                if (pathsOverlap(producedRoot, inputRoot)) {
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
        char sep = File.separatorChar;
        if (path2.length() > path1.length()
            && path2.charAt(path1.length()) == sep
            && path2.startsWith(path1)) {
            return true;
        }
        if (path1.length() > path2.length()
            && path1.charAt(path2.length()) == sep
            && path1.startsWith(path2)) {
            return true;
        }
        return false;
    }

    @Override
    public void setConfigurationCacheHit(boolean configurationCacheHit) {
        this.configurationCacheHit = configurationCacheHit;
    }

    public void setWatchingFileSystem(boolean watchingFileSystem) {
        this.watchingFileSystem = watchingFileSystem;
    }

    @Override
    public void resetForBuildStart() {
        producedOutputRootPaths.clear();
        configurationCacheHit = false;
        watchingError = false;
    }

    @Override
    public void clearChangedPaths() {
        changedPaths.clear();
        hasVfsBaseline = true;
    }
}
