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

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;

/**
 * Used for the global file hash cache, which is in-memory only.
 */
public class GlobalScopeFileTimeStampInspector extends FileTimeStampInspector implements BuildListener {
    static final String NAME = ManagementFactory.getRuntimeMXBean().getName();
    int counter;
    private final Object lock = new Object();
    private long currentTimestamp;
    private final Set<String> filesWithUnreliableTimestamp = new HashSet<String>();
    private final Set<String> filesWithCurrentTimestamp = new HashSet<String>();

    public GlobalScopeFileTimeStampInspector(CacheScopeMapping cacheScopeMapping) {
        super(cacheScopeMapping.getBaseDirectory(null, "file-changes", VersionStrategy.CachePerVersion));
    }

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public void buildStarted(Gradle gradle) {
        counter++;
        if (CachingFileHasher.isLog()) {
            System.out.println("build started, build count: " + counter + " in " + NAME);
        }
        updateOnStartBuild();
        currentTimestamp = getThisBuildTimestamp();
    }

    @Override
    public boolean timestampCanBeUsedToDetectFileChange(String file, long timestamp) {
        synchronized (lock) {
            if (filesWithUnreliableTimestamp.remove(file)) {
                System.out.println("file has unreliable timestamp: " + file);
                return false;
            }
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
            filesWithUnreliableTimestamp.clear();
            if (currentTimestamp == getLastBuildTimestamp()) {
                filesWithUnreliableTimestamp.addAll(filesWithCurrentTimestamp);
            }
            System.out.println("files marked with unreliable timestamp:");
            for (String path : filesWithUnreliableTimestamp) {
                System.out.println("  " + path);
            }
            System.out.println("done");
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
