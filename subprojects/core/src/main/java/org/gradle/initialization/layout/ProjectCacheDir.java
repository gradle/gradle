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

package org.gradle.initialization.layout;

import org.gradle.cache.internal.DefaultCleanupProgressMonitor;
import org.gradle.cache.internal.VersionSpecificCacheCleanupAction;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

@ServiceScope(Scopes.BuildSession.class)
public class ProjectCacheDir implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectCacheDir.class);

    private static final long MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS = 7;

    private final File dir;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Deleter deleter;
    private boolean deleteOnStop = false;

    public ProjectCacheDir(File dir, ProgressLoggerFactory progressLoggerFactory, Deleter deleter) {
        this.dir = dir;
        this.progressLoggerFactory = progressLoggerFactory;
        this.deleter = deleter;
    }

    public File getDir() {
        return dir;
    }

    public void delete() {
        deleteOnStop = true;
    }

    @Override
    public void stop() {
        if (deleteOnStop) {
            try {
                deleter.deleteRecursively(dir);
            } catch (IOException e) {
                LOGGER.debug("Failed to delete unused project cache dir " + dir.getAbsolutePath(), e);
            }
            return;
        }
        VersionSpecificCacheCleanupAction cleanupAction = new VersionSpecificCacheCleanupAction(
            dir,
            MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS,
            deleter
        );
        String description = cleanupAction.getDisplayName();
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(ProjectCacheDir.class).start(description, description);
        try {
            cleanupAction.execute(new DefaultCleanupProgressMonitor(progressLogger));
        } finally {
            progressLogger.completed();
        }
    }
}
