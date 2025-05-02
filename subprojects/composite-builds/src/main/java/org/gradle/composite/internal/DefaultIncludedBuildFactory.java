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

package org.gradle.composite.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildFactory;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class DefaultIncludedBuildFactory implements IncludedBuildFactory {
    private final BuildTreeState buildTree;
    private final Instantiator instantiator;

    public DefaultIncludedBuildFactory(
        BuildTreeState buildTree,
        Instantiator instantiator
    ) {
        this.buildTree = buildTree;
        this.instantiator = instantiator;
    }

    private static void validateBuildDirectory(File dir) {
        if (!dir.exists()) {
            throw new InvalidUserDataException(String.format("Included build '%s' does not exist.", dir));
        }
        if (!dir.isDirectory()) {
            throw new InvalidUserDataException(String.format("Included build '%s' is not a directory.", dir));
        }
    }

    @Override
    public IncludedBuildState createBuild(BuildIdentifier buildIdentifier, BuildDefinition buildDefinition, boolean isImplicit, BuildState owner) {
        validateBuildDirectory(buildDefinition.getBuildRootDir());
        return new DefaultIncludedBuild(
            buildIdentifier,
            buildDefinition,
            isImplicit,
            owner,
            buildTree,
            instantiator
        );
    }
}
