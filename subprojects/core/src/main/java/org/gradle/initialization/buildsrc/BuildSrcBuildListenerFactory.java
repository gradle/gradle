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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collections;

import static org.gradle.api.internal.tasks.TaskDependencyUtil.getDependenciesForInternalUse;

@ServiceScope(Scopes.Build.class)
public class BuildSrcBuildListenerFactory {
    private final Action<ProjectInternal> buildSrcRootProjectConfiguration;
    private ScriptClassPathResolver resolver;

    public BuildSrcBuildListenerFactory(Action<ProjectInternal> buildSrcRootProjectConfiguration, ScriptClassPathResolver resolver) {
        this.buildSrcRootProjectConfiguration = buildSrcRootProjectConfiguration;
        this.resolver = resolver;
    }

    Listener create() {
        return new Listener(buildSrcRootProjectConfiguration, resolver);
    }

    /**
     * Inspects the build when configured, and adds the appropriate task to build the "main" `buildSrc` component.
     * On build completion, makes the runtime classpath of the main `buildSrc` component available.
     */
    public static class Listener extends InternalBuildAdapter implements EntryTaskSelector {
        private Configuration classpathConfiguration;
        private ProjectState rootProjectState;
        private final Action<ProjectInternal> rootProjectConfiguration;
        private final ScriptClassPathResolver resolver;

        private Listener(Action<ProjectInternal> rootProjectConfiguration, ScriptClassPathResolver resolver) {
            this.rootProjectConfiguration = rootProjectConfiguration;
            this.resolver = resolver;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            GradleInternal gradleInternal = (GradleInternal) gradle;
            // Run only those tasks scheduled by this selector and not the default tasks
            gradleInternal.getStartParameter().setTaskRequests(Collections.emptyList());
            ProjectInternal rootProject = gradleInternal.getRootProject();
            rootProjectState = rootProject.getOwner();
            rootProjectConfiguration.execute(rootProject);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void applyTasksTo(Context context, ExecutionPlan plan) {
            rootProjectState.applyToMutableState(rootProject -> {
                classpathConfiguration = rootProject.getConfigurations().resolvableDependencyScopeUnlocked("buildScriptClasspath");
                resolver.prepareClassPath(classpathConfiguration, rootProject.getDependencies());
                classpathConfiguration.getDependencies().add(rootProject.getDependencies().create(rootProject));
                plan.addEntryTasks(getDependenciesForInternalUse(classpathConfiguration));
            });
        }

        public ClassPath getRuntimeClasspath() {
            return rootProjectState.fromMutableState(project -> resolver.resolveClassPath(classpathConfiguration));
        }
    }
}
