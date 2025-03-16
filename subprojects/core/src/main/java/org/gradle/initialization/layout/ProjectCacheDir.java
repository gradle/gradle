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

import org.gradle.cache.CleanupFrequency;
import org.gradle.cache.internal.DefaultCleanupProgressMonitor;
import org.gradle.cache.internal.VersionSpecificCacheCleanupAction;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.TimestampSuppliers;

import java.io.File;

@ServiceScope(Scope.BuildSession.class)
public class ProjectCacheDir implements Stoppable {
    private static final int MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS = 7;

    private final File dir;
    private final BuildOperationRunner buildOperationRunner;
    private final Deleter deleter;

    public ProjectCacheDir(File dir, BuildOperationRunner buildOperationRunner, Deleter deleter) {
        this.dir = dir;
        this.buildOperationRunner = buildOperationRunner;
        this.deleter = deleter;
    }

    public File getDir() {
        return dir;
    }

    @Override
    public void stop() {
        VersionSpecificCacheCleanupAction cleanupAction = new VersionSpecificCacheCleanupAction(
            dir,
            TimestampSuppliers.daysAgo(MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS),
            deleter,
            CleanupFrequency.DAILY
        );
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                cleanupAction.execute(new DefaultCleanupProgressMonitor(context));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(cleanupAction.getDisplayName());
            }
        });
    }
}
