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
import org.gradle.initialization.BuildController;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.internal.UncheckedException;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

class DelegatingBuildModelAction<T> implements GradleLauncherAction<T>, Serializable {
    private transient GradleLauncherAction<T> action;
    private final Class<? extends T> type;
    private final boolean runTasks;

    public DelegatingBuildModelAction(Class<T> type, boolean runTasks) {
        this.type = type;
        this.runTasks = runTasks;
    }

    public T getResult() {
        return action.getResult();
    }

    public BuildResult run(BuildController buildController) {
        loadAction((DefaultGradleLauncher) buildController.getLauncher());
        return action.run(buildController);
    }

    @SuppressWarnings("unchecked")
    private void loadAction(DefaultGradleLauncher launcher) {
        ClassLoaderRegistry classLoaderRegistry = launcher.getGradle().getServices().get(ClassLoaderRegistry.class);
        try {
            action = (GradleLauncherAction<T>) classLoaderRegistry.getRootClassLoader().loadClass("org.gradle.tooling.internal.provider.BuildModelAction").getConstructor(Class.class, Boolean.TYPE).newInstance(type, runTasks);
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
