/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Used for the global file hash cache, which is in-memory only.
 */
public class GlobalScopeFileTimeStampInspector extends FileTimeStampInspector implements BuildListener {
    private CachingFileHasher fileHasher;
    private final Object lock = new Object();
    private long currentTimestamp;
    private final Set<String> filesWithCurrentTimestamp = new HashSet<String>();

    public GlobalScopeFileTimeStampInspector(CacheScopeMapping cacheScopeMapping) {
        super(cacheScopeMapping.getBaseDirectory(null, "file-changes", VersionStrategy.CachePerVersion));
    }

    public void attach(CachingFileHasher fileHasher) {
        this.fileHasher = fileHasher;
    }

    @Override
    public void buildStarted(Gradle gradle) {
        updateOnStartBuild();
        currentTimestamp = currentTimestamp();
    }

    @Override
    public boolean timestampCanBeUsedToDetectFileChange(String file, long timestamp) {
        synchronized (lock) {
            if (timestamp == currentTimestamp) {
                filesWithCurrentTimestamp.add(file);
            } else if (timestamp > currentTimestamp) {
                filesWithCurrentTimestamp.clear();
                filesWithCurrentTimestamp.add(file);
                currentTimestamp = timestamp;
            }
        }

        return super.timestampCanBeUsedToDetectFileChange(file, timestamp);
    }

    @Override
    public void buildFinished(BuildResult result) {
        updateOnFinishBuild();
        synchronized (lock) {
            try {
                // These files have an unreliable timestamp - discard any cached state for them and rehash next time they are seen
                if (currentTimestamp == getLastBuildTimestamp()) {
                    for (String path : filesWithCurrentTimestamp) {
                        fileHasher.discard(path);
                    }
                }
            } finally {
                filesWithCurrentTimestamp.clear();
            }
        }
    }

    @Override
    public void settingsEvaluated(Settings settings) {
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
    }
}
