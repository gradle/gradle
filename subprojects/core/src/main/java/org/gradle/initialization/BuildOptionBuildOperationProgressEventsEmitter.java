/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.buildtree.BuildTreeLifecycleListener;
import org.gradle.internal.configurationcache.options.ConfigurationCacheSettingsFinalizedProgressDetails;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.operations.initialization.ProjectIsolationSettingsFinalizedProgressDetails;

import javax.inject.Inject;

@ServiceScope(Scopes.BuildTree.class)
public class BuildOptionBuildOperationProgressEventsEmitter implements BuildTreeLifecycleListener {

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final BuildModelParameters buildModelParameters;

    @Inject
    public BuildOptionBuildOperationProgressEventsEmitter(BuildOperationProgressEventEmitter eventEmitter, BuildModelParameters buildModelParameters) {
        this.eventEmitter = eventEmitter;
        this.buildModelParameters = buildModelParameters;
    }

    @Override
    public void afterStart() {
        eventEmitter.emitNowForCurrent(new ConfigurationCacheSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return buildModelParameters.isConfigurationCache();
            }
        });

        eventEmitter.emitNowForCurrent(new ProjectIsolationSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return buildModelParameters.isIsolatedProjects();
            }
        });
    }
}
