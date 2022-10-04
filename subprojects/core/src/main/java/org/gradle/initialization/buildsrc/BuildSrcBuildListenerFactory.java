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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.DefaultScriptClassPathResolver;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
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

@ServiceScope(Scopes.Build.class)
public class BuildSrcBuildListenerFactory {

    private final Action<ProjectInternal> buildSrcRootProjectConfiguration;
    private final NamedObjectInstantiator instantiator;

    public BuildSrcBuildListenerFactory(Action<ProjectInternal> buildSrcRootProjectConfiguration, NamedObjectInstantiator instantiator) {
        this.buildSrcRootProjectConfiguration = buildSrcRootProjectConfiguration;
        this.instantiator = instantiator;
    }

    Listener create() {
        return new Listener(buildSrcRootProjectConfiguration, instantiator);
    }

    /**
     * Inspects the build when configured, and adds the appropriate task to build the "main" `buildSrc` component.
     * On build completion, makes the runtime classpath of the main `buildSrc` component available.
     */
    public static class Listener extends InternalBuildAdapter implements EntryTaskSelector {
        private Configuration classpathConfiguration;
        private ProjectState rootProjectState;
        private final Action<ProjectInternal> rootProjectConfiguration;
        private final NamedObjectInstantiator instantiator;

        private Listener(Action<ProjectInternal> rootProjectConfiguration, NamedObjectInstantiator instantiator) {
            this.rootProjectConfiguration = rootProjectConfiguration;
            this.instantiator = instantiator;
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
        public void applyTasksTo(Context context, ExecutionPlan plan) {
            rootProjectState.applyToMutableState(rootProject -> {
                classpathConfiguration = rootProject.getConfigurations().create("buildScriptClasspath");
                // TODO - share this with DefaultScriptHandler
                classpathConfiguration.setCanBeConsumed(false);
                AttributeContainer attributes = classpathConfiguration.getAttributes();
                attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, instantiator.named(Category.class, Category.LIBRARY));
                attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, instantiator.named(LibraryElements.class, LibraryElements.JAR));
                attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, instantiator.named(Bundling.class, Bundling.EXTERNAL));
                classpathConfiguration.getDependencies().add(rootProject.getDependencies().create(rootProject));
                plan.addEntryTasks(classpathConfiguration.getBuildDependencies().getDependencies(null));
            });
        }

        public ClassPath getRuntimeClasspath() {
            return rootProjectState.fromMutableState(project -> {
                ScriptClassPathResolver resolver = new DefaultScriptClassPathResolver(Collections.emptyList());
                return resolver.resolveClassPath(classpathConfiguration);
            });
        }
    }
}
