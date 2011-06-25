/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.util.UncheckedException;

import java.io.Serializable;

class DelegatingBuildModelAction implements GradleLauncherAction<ProjectVersion3>, Serializable {
    private transient GradleLauncherAction<ProjectVersion3> action;
    private final Class<? extends ProjectVersion3> type;

    public DelegatingBuildModelAction(Class<? extends ProjectVersion3> type) {
        this.type = type;
    }

    public ProjectVersion3 getResult() {
        return action.getResult();
    }

    public BuildResult run(GradleLauncher launcher) {
        loadAction((DefaultGradleLauncher) launcher);
        return action.run(launcher);
    }

    private void loadAction(DefaultGradleLauncher launcher) {
        DefaultGradleLauncher gradleLauncher = launcher;
        ClassLoaderRegistry classLoaderRegistry = gradleLauncher.getGradle().getServices().get(ClassLoaderRegistry.class);
        try {
            action = (GradleLauncherAction<ProjectVersion3>) classLoaderRegistry.getRootClassLoader().loadClass("org.gradle.tooling.internal.provider.BuildModelAction").getConstructor(Class.class).newInstance(type);
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }
}
