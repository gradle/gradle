/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;

import java.io.File;

public class DefaultBuildIdentity implements BuildIdentity {
    private final BuildIdentifier buildIdentifier;

    public DefaultBuildIdentity(BuildDefinition buildDefinition, boolean isRootBuild) {
        if (isRootBuild) {
            this.buildIdentifier = DefaultBuildIdentifier.ROOT;
        } else {
            // Infer a build id from the containing directory
            // Should be part of the build definition
            File dir = buildDefinition.getBuildRootDir() == null ? buildDefinition.getStartParameter().getCurrentDir() : buildDefinition.getBuildRootDir();
            this.buildIdentifier = new DefaultBuildIdentifier(dir.getName());
        }
    }

    @Override
    public BuildIdentifier getCurrentBuild() {
        return buildIdentifier;
    }
}
