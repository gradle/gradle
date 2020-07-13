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

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.configurationcache.options.ConfigurationCacheSettingsFinalizedProgressDetails;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails;

import javax.inject.Inject;

public class BuildOptionBuildOperationProgressEventsEmitter {

    private final BuildOperationProgressEventEmitter eventEmitter;

    @Inject
    public BuildOptionBuildOperationProgressEventsEmitter(BuildOperationProgressEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }

    @SuppressWarnings({"Anonymous2MethodRef", "Convert2Lambda"})
    public void emit(StartParameterInternal startParameterInternal) {
        eventEmitter.emitNowForCurrent(new ConfigurationCacheSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return startParameterInternal.isConfigurationCache();
            }
        });
        eventEmitter.emitNowForCurrent(new FileSystemWatchingSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return startParameterInternal.isWatchFileSystem();
            }
        });
    }
}
