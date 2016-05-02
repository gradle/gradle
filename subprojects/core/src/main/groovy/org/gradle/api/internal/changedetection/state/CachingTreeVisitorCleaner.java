/*
 * Copyright 2016 the original author or authors.
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

import java.io.Closeable;
import java.io.IOException;

public class CachingTreeVisitorCleaner implements Closeable {
    private final Gradle gradle;
    private final CacheCleaner buildListener;

    public CachingTreeVisitorCleaner(CachingTreeVisitor cachingTreeVisitor, Gradle gradle) {
        this.gradle = gradle;
        buildListener = new CacheCleaner(cachingTreeVisitor);
        gradle.addBuildListener(buildListener);
    }

    @Override
    public void close() throws IOException {
        gradle.removeListener(buildListener);
    }

    private static class CacheCleaner implements BuildListener {
        private final CachingTreeVisitor cachingTreeVisitor;

        public CacheCleaner(CachingTreeVisitor cachingTreeVisitor) {
            this.cachingTreeVisitor = cachingTreeVisitor;
        }

        @Override
        public void buildFinished(BuildResult result) {
            cachingTreeVisitor.clearCache();
        }

        @Override
        public void buildStarted(Gradle gradle) {

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
}
