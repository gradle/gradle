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

import org.gradle.cache.internal.VersionSpecificCacheCleanupAction;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;

public class ProjectCacheDir implements Stoppable {

    private static final long MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS = 7;

    private final File dir;
    private final BuildOperationExecutor buildOperationExecutor;

    public ProjectCacheDir(File dir, BuildOperationExecutor buildOperationExecutor) {
        this.dir = dir;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public File getDir() {
        return dir;
    }

    @Override
    public void stop() {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                new VersionSpecificCacheCleanupAction(dir, MAX_UNUSED_DAYS_FOR_RELEASES_AND_SNAPSHOTS).execute();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Delete unused version-specific caches in " + dir);
            }
        });
    }
}
