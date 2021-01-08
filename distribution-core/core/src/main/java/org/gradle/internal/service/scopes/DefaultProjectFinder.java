/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.function.Supplier;

public class DefaultProjectFinder implements ProjectFinder {
    private final Supplier<ProjectInternal> baseProjectSupplier;

    public DefaultProjectFinder(Supplier<ProjectInternal> baseProjectSupplier) {
        this.baseProjectSupplier = baseProjectSupplier;
    }

    @Override
    public ProjectInternal getProject(String path) {
        return baseProjectSupplier.get().project(path);
    }
}
