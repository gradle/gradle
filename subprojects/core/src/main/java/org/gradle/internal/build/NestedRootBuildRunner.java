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
import org.gradle.api.Transformer;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;

public class NestedRootBuildRunner {

    public static StartParameter createStartParameterForNewBuild(ServiceRegistry services) {
        return services.get(StartParameter.class).newBuild();
    }

    public static void runNestedRootBuild(String buildName, StartParameter startParameter, ServiceRegistry services) {
        createNestedRootBuild(buildName, startParameter, services).run((Transformer<Void, BuildController>) buildController -> {
            buildController.run();
            return null;
        });
    }

    public static NestedRootBuild createNestedRootBuild(@Nullable String buildName, StartParameter startParameter, ServiceRegistry services) {
        PublicBuildPath fromBuild = services.get(PublicBuildPath.class);
        BuildDefinition buildDefinition = BuildDefinition.fromStartParameter(startParameter, fromBuild);

        BuildState currentBuild = services.get(BuildState.class);

        NestedRootBuild nestedBuild;

        // buildStateRegistry is not threadsafe, but this is the only concurrent use currently
        BuildStateRegistry buildStateRegistry = services.get(BuildStateRegistry.class);
        synchronized (buildStateRegistry) {
            nestedBuild = buildStateRegistry.addNestedBuildTree(buildDefinition, currentBuild, buildName);
        }
        return nestedBuild;
    }
}
