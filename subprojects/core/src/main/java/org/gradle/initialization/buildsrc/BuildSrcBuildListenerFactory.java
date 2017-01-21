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

import org.gradle.BuildAdapter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.component.BuildableJavaComponent;
import org.gradle.api.internal.component.ComponentRegistry;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.ModelConfigurationListener;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class BuildSrcBuildListenerFactory {

    Listener create(boolean rebuild) {
        return new Listener(rebuild);
    }

    public static class Listener extends BuildAdapter implements ModelConfigurationListener {
        private Set<File> classpath;
        private final boolean rebuild;

        public Listener(boolean rebuild) {
            this.rebuild = rebuild;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            Project rootProject = gradle.getRootProject();

            rootProject.getPluginManager().apply("groovy");

            DependencyHandler dependencies = rootProject.getDependencies();
            dependencies.add("compile", dependencies.gradleApi());
            dependencies.add("compile", dependencies.localGroovy());
        }

        public Collection<File> getRuntimeClasspath() {
            return classpath;
        }

        public void onConfigure(GradleInternal gradle) {
            BuildableJavaComponent projectInfo = gradle.getRootProject().getServices().get(ComponentRegistry.class).getMainComponent();
            gradle.getStartParameter().setTaskNames(rebuild ? projectInfo.getRebuildTasks() : projectInfo.getBuildTasks());
            classpath = projectInfo.getRuntimeClasspath().getFiles();
        }
    }
}
