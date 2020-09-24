/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.classpath.ClassPath;

public class BuildTreePreparingProjectsPreparer implements ProjectsPreparer {
    private final ProjectsPreparer delegate;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildSourceBuilder buildSourceBuilder;
    private final BuildLoader buildLoader;

    public BuildTreePreparingProjectsPreparer(ProjectsPreparer delegate, BuildLoader buildLoader, BuildStateRegistry buildStateRegistry, BuildSourceBuilder buildSourceBuilder) {
        this.delegate = delegate;
        this.buildLoader = buildLoader;
        this.buildStateRegistry = buildStateRegistry;
        this.buildSourceBuilder = buildSourceBuilder;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        // Setup classloader for root project, all other projects will be derived from this.
        SettingsInternal settings = gradle.getSettings();
        ClassLoaderScope parentClassLoaderScope = settings.getClassLoaderScope();
        ClassLoaderScope baseProjectClassLoaderScope = parentClassLoaderScope.createChild(settings.getBuildSrcDir().getAbsolutePath());
        gradle.setBaseProjectClassLoaderScope(baseProjectClassLoaderScope);

        // attaches root project
        buildLoader.load(gradle.getSettings(), gradle);
        // Makes included build substitutions available
        if (gradle.isRootBuild()) {
            buildStateRegistry.beforeConfigureRootBuild();
        }
        // Build buildSrc and export classpath to root project
        buildBuildSrcAndLockClassloader(gradle, baseProjectClassLoaderScope);

        delegate.prepareProjects(gradle);
    }

    private void buildBuildSrcAndLockClassloader(GradleInternal gradle, ClassLoaderScope baseProjectClassLoaderScope) {
        ClassPath buildSrcClassPath = buildSourceBuilder.buildAndGetClassPath(gradle);
        baseProjectClassLoaderScope.export(buildSrcClassPath).lock();
    }
}
