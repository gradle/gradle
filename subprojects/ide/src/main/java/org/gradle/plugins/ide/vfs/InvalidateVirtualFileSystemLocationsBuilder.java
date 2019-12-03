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

package org.gradle.plugins.ide.vfs;

import org.gradle.api.Project;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.tooling.model.build.InvalidatedPaths;
import org.gradle.tooling.model.build.PathsToInvalidate;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

public class InvalidateVirtualFileSystemLocationsBuilder implements ParameterizedToolingModelBuilder<PathsToInvalidate> {

    private static final InvalidatedPaths INVALIDATED_PATHS = new InvalidatedPaths() {};

    private final VirtualFileSystem virtualFileSystem;

    public InvalidateVirtualFileSystemLocationsBuilder(VirtualFileSystem virtualFileSystem) {
        this.virtualFileSystem = virtualFileSystem;
    }

    @Override
    public Class<PathsToInvalidate> getParameterType() {
        return PathsToInvalidate.class;
    }

    @Override
    public InvalidatedPaths buildAll(String modelName, PathsToInvalidate parameter, Project project) {
        virtualFileSystem.update(parameter.getPaths(), () -> {});
        return INVALIDATED_PATHS;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.build.InvalidatedPaths");
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        throw new UnsupportedOperationException("Invalidate paths model requires parameter of type PathsToInvalidate");
    }
}
