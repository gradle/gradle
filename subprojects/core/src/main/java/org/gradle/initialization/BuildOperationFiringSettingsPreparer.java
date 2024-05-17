/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;

public class BuildOperationFiringSettingsPreparer implements SettingsPreparer {
    private static final LoadBuildBuildOperationType.Result RESULT = new LoadBuildBuildOperationType.Result() {
    };

    private final SettingsPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationProgressEventEmitter emitter;
    @Nullable
    private final PublicBuildPath fromBuild;

    public BuildOperationFiringSettingsPreparer(SettingsPreparer delegate, BuildOperationExecutor buildOperationExecutor, BuildOperationProgressEventEmitter emitter, @Nullable PublicBuildPath fromBuild) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.emitter = emitter;
        this.fromBuild = fromBuild;
    }

    @Override
    public void prepareSettings(GradleInternal gradle) {
        emitter.emitNowForCurrent(new BuildIdentifiedProgressDetails() {
            @Override
            public String getBuildPath() {
                return gradle.getIdentityPath().toString();
            }
        });
        buildOperationExecutor.run(new LoadBuild(gradle));
    }

    private class LoadBuild implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public LoadBuild(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext context) {
            doLoadBuild();
            context.setResult(RESULT);
        }

        void doLoadBuild() {
            delegate.prepareSettings(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Load build"))
                .details(new LoadBuildBuildOperationType.Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().toString();
                    }

                    @Override
                    public String getIncludedBy() {
                        return fromBuild == null ? null : fromBuild.getBuildPath().toString();
                    }
                });
        }
    }

}
