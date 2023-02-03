/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.StartParameter;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.buildtree.NestedBuildTree;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;

public class NestedRootBuildRunner {

    public static StartParameter createStartParameterForNewBuild(ServiceRegistry services) {
        return services.get(StartParameter.class).newBuild();
    }

    public static void runNestedRootBuild(String buildName, StartParameterInternal startParameter, ServiceRegistry services) {
        createNestedBuildTree(buildName, startParameter, services).run(buildController -> {
            buildController.scheduleAndRunTasks();
            return null;
        });
    }

    public static NestedBuildTree createNestedBuildTree(@Nullable String buildName, StartParameterInternal startParameter, ServiceRegistry services) {
        PublicBuildPath fromBuild = services.get(PublicBuildPath.class);
        BuildDefinition buildDefinition = BuildDefinition.fromStartParameter(startParameter, fromBuild);

        BuildState currentBuild = services.get(BuildState.class);

        BuildStateRegistry buildStateRegistry = services.get(BuildStateRegistry.class);
        return buildStateRegistry.addNestedBuildTree(buildDefinition, currentBuild, buildName);
    }
}
