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

import java.lang.management.ManagementFactory;

/**
 * Used for the global file hash cache, which is in-memory only.
 */
public class NonPersistentCacheFileTimestampInspector extends FileTimeStampInspector implements BuildListener {
    static final String NAME = ManagementFactory.getRuntimeMXBean().getName();
    int counter;

    @Override
    public void buildStarted(Gradle gradle) {
        counter++;
        // Should probably use the same filesystem as the build root dir. However, this isn't known at this point in time
        thisBuildTimestamp = currentTimestamp(null);
        if (CachingFileHasher.isLog()) {
            System.out.println("build started, build count: " + counter + " in " + NAME);
            System.out.println("build started, using this build timestamp: " + thisBuildTimestamp + " in " + NAME);
            System.out.println("build started, using last build timestamp: " + lastBuildTimestamp + " in " + NAME);
        }
    }

    @Override
    public void settingsEvaluated(Settings settings) {
    }

    @Override
    public void buildFinished(BuildResult result) {
        thisBuildTimestamp = 0;
        lastBuildTimestamp = currentTimestamp(null);
        if (CachingFileHasher.isLog()) {
            System.out.println("build finished, using this build timestamp: " + thisBuildTimestamp + " in " + NAME);
            System.out.println("build finished, using last build timestamp: " + lastBuildTimestamp + " in " + NAME);
        }
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
    }
}
