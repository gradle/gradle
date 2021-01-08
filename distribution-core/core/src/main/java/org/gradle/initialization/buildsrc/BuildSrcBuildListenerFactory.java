/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.internal.Actions;
import org.gradle.internal.InternalBuildAdapter;

import java.io.File;
import java.util.Collection;

public class BuildSrcBuildListenerFactory {

    private final Action<ProjectInternal> buildSrcRootProjectConfiguration;

    public BuildSrcBuildListenerFactory() {
        this(Actions.<ProjectInternal>doNothing());
    }

    public BuildSrcBuildListenerFactory(Action<ProjectInternal> buildSrcRootProjectConfiguration) {
        this.buildSrcRootProjectConfiguration = buildSrcRootProjectConfiguration;
    }

    Listener create() {
        return new Listener(buildSrcRootProjectConfiguration);
    }

    /**
     * Inspects the build when configured, and adds the appropriate task to build the "main" `buildSrc` component.
     * On build completion, makes the runtime classpath of the main `buildSrc` component available.
     */
    public static class Listener extends InternalBuildAdapter implements ModelConfigurationListener {
        private FileCollection classpath;
        private ProjectState rootProjectState;
        private final Action<ProjectInternal> rootProjectConfiguration;

        private Listener(Action<ProjectInternal> rootProjectConfiguration) {
            this.rootProjectConfiguration = rootProjectConfiguration;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            rootProjectConfiguration.execute((ProjectInternal) gradle.getRootProject());
        }

        @Override
        public void onConfigure(GradleInternal gradle) {
            final BuildableJavaComponent mainComponent = mainComponentOf(gradle);
            gradle.getStartParameter().setTaskNames(mainComponent.getBuildTasks());
            classpath = mainComponent.getRuntimeClasspath();
            rootProjectState = gradle.getRootProject().getMutationState();
        }

        public Collection<File> getRuntimeClasspath() {
            return rootProjectState.fromMutableState(p -> classpath.getFiles());
        }

        private BuildableJavaComponent mainComponentOf(GradleInternal gradle) {
            return gradle.getRootProject().getServices().get(ComponentRegistry.class).getMainComponent();
        }
    }
}
