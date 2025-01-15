/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.cache.MonitoredCleanupAction;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.versionedcache.UsedGradleVersions;

import java.io.File;

@ServiceScope(Scope.UserHome.class)
public class GradleUserHomeCleanupService implements Stoppable {
    private final Deleter deleter;
    private final GradleUserHomeDirProvider userHomeDirProvider;
    private final GlobalScopedCacheBuilderFactory cacheBuilderFactory;
    private final UsedGradleVersions usedGradleVersions;
    private final BuildOperationRunner buildOperationRunner;
    private final CacheConfigurationsInternal cacheConfigurations;
    private boolean alreadyCleaned;

    public GradleUserHomeCleanupService(
        Deleter deleter,
        GradleUserHomeDirProvider userHomeDirProvider,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        UsedGradleVersions usedGradleVersions,
        BuildOperationRunner buildOperationRunner,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        this.deleter = deleter;
        this.userHomeDirProvider = userHomeDirProvider;
        this.cacheBuilderFactory = cacheBuilderFactory;
        this.usedGradleVersions = usedGradleVersions;
        this.buildOperationRunner = buildOperationRunner;
        this.cacheConfigurations = cacheConfigurations;
    }

    public void cleanup() {
        File cacheBaseDir = cacheBuilderFactory.getRootDir();
        boolean wasCleanedUp = execute(
            new VersionSpecificCacheCleanupAction(
                cacheBaseDir,
                cacheConfigurations.getReleasedWrappers().getEntryRetentionTimestampSupplier(),
                cacheConfigurations.getSnapshotWrappers().getEntryRetentionTimestampSupplier(),
                deleter,
                cacheConfigurations.getCleanupFrequency().get()
            )
        );
        if (wasCleanedUp) {
            execute(new WrapperDistributionCleanupAction(userHomeDirProvider.getGradleUserHomeDirectory(), usedGradleVersions));
        }
        alreadyCleaned = true;
    }

    @Override
    public void stop() {
        if (!alreadyCleaned) {
            cleanup();
        }
    }

    private boolean execute(MonitoredCleanupAction action) {
        return buildOperationRunner.call(new CallableBuildOperation<Boolean>() {
            @Override
            public Boolean call(BuildOperationContext context) throws Exception {
                return action.execute(new DefaultCleanupProgressMonitor(context));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(action.getDisplayName());
            }
        });
    }
}
