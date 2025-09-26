/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

/**
 * Resolves potentially relative string-typed project paths to build-tree project identity {@link Path}s,
 * relative to some base project.
 */
@ServiceScope(Scope.Project.class)
public interface ProjectFinder {

    /**
     * Resolves the given string-typed project path to build-tree project identity {@link Path}.
     * <p>
     * The given path may be a relative path, in which case it is resolved to an absolute project path
     * relative to this project finder's base project. Then, the absolute project path is converted to
     * a build-tree project identity path based on the base project's owning build.
     */
    Path resolveIdentityPath(String path);

}
