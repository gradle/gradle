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

package org.gradle.execution;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.resource.EmptyFileTextResource;
import org.gradle.internal.resource.TextResource;

import java.util.Collection;
import static org.gradle.api.internal.StartParameterInternal.useLocationAsProjectRoot;

public class UndefinedBuildWorkExecutor implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;
    private final ProjectCacheDir projectCacheDir;

    public UndefinedBuildWorkExecutor(BuildWorkExecutor delegate, ProjectCacheDir projectCacheDir) {
        this.delegate = delegate;
        this.projectCacheDir = projectCacheDir;
    }

    @Override
    public void execute(GradleInternal gradle, Collection<? super Throwable> failures) {
        if (!useLocationAsProjectRoot(gradle.getRootProject().getRootDir(), gradle.getStartParameter().getTaskNames()) && isUndefinedBuild(gradle)) {
            projectCacheDir.delete();
            throw new InvalidUserCodeException(
                "Executing Gradle tasks as part of an undefined build is not supported. " +
                "Make sure that you are executing Gradle from a folder within your Gradle project. " +
                "Your project should have a 'settings.gradle(.kts)' file in the root folder.");
        }
        delegate.execute(gradle, failures);
    }

    private static boolean isUndefinedBuild(GradleInternal gradle) {
        BuildState buildState = gradle.getOwner();
        if (buildState instanceof IncludedBuildState && ((IncludedBuildState) buildState).hasInjectedSettingsPlugins()) {
            // this included build may be completely configured through injected plugins
            return false;
        }
        return !gradle.getRootProject().getBuildFile().exists() && isUndefinedResource(gradle.getSettings().getSettingsScript().getResource());
    }

    private static boolean isUndefinedResource(TextResource settingsScript) {
        return settingsScript instanceof EmptyFileTextResource && settingsScript.getFile() == null;
    }
}
