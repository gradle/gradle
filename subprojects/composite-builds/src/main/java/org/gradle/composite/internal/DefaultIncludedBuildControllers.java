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

package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.initialization.IncludedBuilds;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;

import java.util.Map;

class DefaultIncludedBuildControllers implements Stoppable, IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = Maps.newHashMap();
    private final IncludedBuilds includedBuilds;

    DefaultIncludedBuildControllers(IncludedBuilds includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        IncludedBuild build = includedBuilds.getBuild(buildId.getName());
        DefaultIncludedBuildController newBuildController = new DefaultIncludedBuildController(build);
        buildControllers.put(buildId, newBuildController);

        // TODO:DAZ Do this properly
        new Thread(newBuildController).start();

        return newBuildController;
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
    }
}
