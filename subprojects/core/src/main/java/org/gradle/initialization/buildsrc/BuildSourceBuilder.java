/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.BuildLogicBuildQueue;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.Build.class)
public class BuildSourceBuilder {
    private static final BuildBuildSrcBuildOperationType.Result BUILD_BUILDSRC_RESULT = new BuildBuildSrcBuildOperationType.Result() {
    };

    private final BuildState currentBuild;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildSrcBuildListenerFactory buildSrcBuildListenerFactory;
    private final BuildStateRegistry buildRegistry;
    private final PublicBuildPath publicBuildPath;
    private final BuildLogicBuildQueue buildQueue;

    public BuildSourceBuilder(
        BuildState currentBuild,
        BuildOperationExecutor buildOperationExecutor,
        BuildSrcBuildListenerFactory buildSrcBuildListenerFactory,
        BuildStateRegistry buildRegistry,
        PublicBuildPath publicBuildPath,
        BuildLogicBuildQueue buildQueue
    ) {
        this.currentBuild = currentBuild;
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildSrcBuildListenerFactory = buildSrcBuildListenerFactory;
        this.buildRegistry = buildRegistry;
        this.publicBuildPath = publicBuildPath;
        this.buildQueue = buildQueue;
    }

    public ClassPath buildAndGetClassPath(GradleInternal gradle) {
        StandAloneNestedBuild buildSrcBuild = buildRegistry.getBuildSrcNestedBuild(currentBuild);
        if (buildSrcBuild == null) {
            return ClassPath.EMPTY;
        }

        return buildOperationExecutor.call(new CallableBuildOperation<ClassPath>() {
            @Override
            public ClassPath call(BuildOperationContext context) {
                ClassPath classPath = buildBuildSrc(buildSrcBuild);
                context.setResult(BUILD_BUILDSRC_RESULT);
                return classPath;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                //noinspection Convert2Lambda
                return BuildOperationDescriptor.displayName("Build buildSrc").
                    progressDisplayName("Building buildSrc").
                    details(
                        new BuildBuildSrcBuildOperationType.Details() {
                            @Override
                            public String getBuildPath() {
                                return publicBuildPath.getBuildPath().toString();
                            }
                        }
                    );
            }
        });
    }

    private ClassPath buildBuildSrc(StandAloneNestedBuild buildSrcBuild) {
        return buildQueue.buildBuildSrc(buildSrcBuild, buildController ->
            new BuildSrcUpdateFactory(buildSrcBuildListenerFactory).create(buildController)
        );
    }
}
