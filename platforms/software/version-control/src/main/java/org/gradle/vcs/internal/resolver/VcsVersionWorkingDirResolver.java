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

package org.gradle.vcs.internal.resolver;

import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.vcs.internal.VersionControlRepositoryConnection;

import javax.annotation.Nullable;
import java.io.File;

@ServiceScope(Scope.Build.class)
public interface VcsVersionWorkingDirResolver {
    /**
     * Attempts to locate a matching version for the selector in the given VCS, returning the working directory to use for the version if found.
     *
     * @return The working directory or {@code null} if not found.
     */
    @Nullable
    File selectVersion(ModuleComponentSelector selector, VersionControlRepositoryConnection repository);
}
