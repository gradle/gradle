/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.dependencies.Dependency;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DependencyFactory {
    List<Class> dependencyImplementations;

    DependencyFactory(List<Class> dependencyImplementations) {
        this.dependencyImplementations = dependencyImplementations;
    }

    Dependency createDependency(Set confs, Object userDependencyDescription, Project project) {
        Dependency dependency = null;

        if (userDependencyDescription instanceof Dependency) {
            dependency = (Dependency) userDependencyDescription;
            dependency.setProject(project);
            dependency.setConfs(confs);
        } else {
            for (Class dependencyType : dependencyImplementations) {
                try {
                    Constructor constructor = dependencyType.getDeclaredConstructor(Set.class, Object.class, Project.class);
                    dependency = (Dependency) constructor.newInstance(confs, userDependencyDescription, project);
                }
                catch (InvocationTargetException e) {
                    if (e.getCause() != null && !(e.getCause() instanceof UnknownDependencyNotation)) {
                        throw new GradleException(e);
                    }
                }
                catch (Exception e) {
                    throw new GradleException("Dependency creation error.", e);
                }
            }
        }
        if (dependency == null) {
            throw new InvalidUserDataException("The dependency notation: " + userDependencyDescription + " is invalid!");
        }
        dependency.initialize();
        return dependency;
    }

}
