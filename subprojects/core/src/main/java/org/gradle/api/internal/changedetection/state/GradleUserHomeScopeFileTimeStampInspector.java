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

import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.HashSet;
import java.util.Set;

/**
 * Used for the Gradle user home file hash cache.
 *
 * Uses the same strategy for detection of file changes as {@link FileTimeStampInspector}.
 *
 * Discards hashes for all files from the {@link CachingFileHasher} which have been queried on this daemon
 * during the last build and which have a timestamp equal to the end of build timestamp.
 */
@ServiceScope(Scopes.UserHome.class)
public class GradleUserHomeScopeFileTimeStampInspector extends FileTimeStampInspector implements RootBuildLifecycleListener {
    private CachingFileHasher fileHasher;
    private final Object lock = new Object();
    private long currentTimestamp;
    private final Set<String> filesWithCurrentTimestamp = new HashSet<>();
    private boolean isCurrentTimestampHighPrecision;

    public GradleUserHomeScopeFileTimeStampInspector(GlobalScopedCacheBuilderFactory cacheBuilderFactory) {
        super(cacheBuilderFactory.baseDirForCache("file-changes"));
    }

    public void attach(CachingFileHasher fileHasher) {
        this.fileHasher = fileHasher;
    }

    @Override
    public void afterStart() {
        updateOnStartBuild();
        currentTimestamp = currentTimestamp();
        isCurrentTimestampHighPrecision = isHighTimestampPrecision(currentTimestamp);
    }

    @Override
    public boolean timestampCanBeUsedToDetectFileChange(String file, long timestamp) {
        if (!isReliableTimestampPrecision(timestamp)) {
            synchronized (lock) {
                if (timestamp == currentTimestamp) {
                    filesWithCurrentTimestamp.add(file);
                } else if (timestamp > currentTimestamp) {
                    filesWithCurrentTimestamp.clear();
                    filesWithCurrentTimestamp.add(file);
                    currentTimestamp = timestamp;
                }
            }
        }

        return super.timestampCanBeUsedToDetectFileChange(file, timestamp);
    }

    @Override
    public void beforeComplete() {
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

    /**
     * Detects whether the file system has a reliable high precision timestamp.
     *
     * If `isCurrentTimestampHighPrecision` is in `millisecond` precision, then all file timestamps will have `millisecond` precision
     * since we check all Java APIs that can return a high or a low precision, and we choose the one with the lowest precision.
     *
     * In case `isCurrentTimestampHighPrecision` is in the `second` precision, then we check also the provided timestamp.
     * And if the provided timestamp is in `millisecond` precision, then we will definitely detect changes for that file correctly.
     * But if it's in `second` precision we might not detect changes, and we might need to discard the hash at the end of the build.
     */
    private boolean isReliableTimestampPrecision(long timestamp) {
        return isCurrentTimestampHighPrecision || isHighTimestampPrecision(timestamp);
    }

    private static boolean isHighTimestampPrecision(long fileTimestamp) {
        return fileTimestamp % 1000 != 0;
    }
}
