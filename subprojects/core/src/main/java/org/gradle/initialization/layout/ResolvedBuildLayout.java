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

package org.gradle.initialization.layout;

import org.gradle.api.internal.GradleInternal;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Contains information about the build layout, resolved after running the settings script and selecting the default project.
 */
@ServiceScope(Scopes.Build.class)
public class ResolvedBuildLayout {
    private final GradleInternal gradle;
    private final BuildLayout buildLayout;
    private final BuildScopedCacheBuilderFactory cacheBuilderFactory;

    public ResolvedBuildLayout(GradleInternal gradle, BuildLayout buildLayout, BuildScopedCacheBuilderFactory cacheBuilderFactory) {
        this.gradle = gradle;
        this.buildLayout = buildLayout;
        this.cacheBuilderFactory = cacheBuilderFactory;
    }

    /**
     * Returns the directory that Gradle was invoked on (taking command-line options such as --project-dir into account).
     */
    public File getCurrentDirectory() {
        return gradle.getStartParameter().getCurrentDir();
    }

    public File getGlobalScopeCacheDirectory() {
        return gradle.getGradleUserHomeDir();
    }

    public File getBuildScopeCacheDirectory() {
        return cacheBuilderFactory.getRootDir();
    }

    /**
     * Is the build using an empty settings because a build definition is missing from the current directory?
     *
     * <p>There are two cases where this might be true: Gradle was invoked from a directory where there is no build script and no settings script in the directory hierarchy,
     * or Gradle was invoked from a directory where there is a settings script in the directory hierarchy but the directory is not a project directory for any project defined
     * in that settings script.</p>
     */
    public boolean isBuildDefinitionMissing() {
        boolean isNoBuildDefinitionFound = buildLayout.isBuildDefinitionMissing();
        boolean isCurrentDirNotPartOfContainingBuild = gradle.getSettings().getSettingsScript().getResource().getLocation().getFile() == null;
        return isNoBuildDefinitionFound || isCurrentDirNotPartOfContainingBuild;
    }
}
