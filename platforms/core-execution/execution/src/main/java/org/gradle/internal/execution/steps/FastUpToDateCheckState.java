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

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scope.UserHome.class)
public class FastUpToDateCheckState implements FastUpToDateCheckLifecycle {
    private final Set<Path> changedPaths = ConcurrentHashMap.newKeySet();
    private volatile boolean anyTaskTouched;
    private volatile boolean configurationCacheHit;
    private volatile boolean watchingFileSystem;

    public void recordChange(Path path) {
        changedPaths.add(path);
    }

    public void stopWatchingAfterError() {
        watchingFileSystem = false;
        changedPaths.clear();
    }

    public boolean isAnyTaskTouched() {
        return anyTaskTouched;
    }

    public void setAnyTaskTouched(boolean anyTaskTouched) {
        this.anyTaskTouched = anyTaskTouched;
    }

    public boolean isConfigurationCacheHit() {
        return configurationCacheHit;
    }

    @Override
    public void setConfigurationCacheHit(boolean configurationCacheHit) {
        this.configurationCacheHit = configurationCacheHit;
    }

    public boolean isWatchingFileSystem() {
        return watchingFileSystem;
    }

    public void setWatchingFileSystem(boolean watchingFileSystem) {
        this.watchingFileSystem = watchingFileSystem;
    }

    public Set<Path> getChangedPaths() {
        return changedPaths;
    }

    @Override
    public void resetForBuildStart() {
        anyTaskTouched = false;
        configurationCacheHit = false;
    }

    @Override
    public void clearChangedPaths() {
        changedPaths.clear();
    }
}
