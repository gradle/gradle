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

package org.gradle.api.internal.dependencies

import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.Dependency
import org.gradle.api.internal.project.DefaultProject

/**
* @author Hans Dockter
*/
class DependencyFactory {
    List dependencyImplementations

    DependencyFactory(List dependencyImplementations) {
        this.dependencyImplementations = dependencyImplementations
    }

    Dependency createDependency(Set confs, Object userDependencyDescription, DefaultProject project) {
        Dependency dependency = null

        if (userDependencyDescription instanceof Dependency) {
            dependency = userDependencyDescription
            dependency.project = project
            dependency.confs = confs
        } else {
            for (Class clazz in dependencyImplementations) {
                try {
                    dependency = clazz.newInstance(confs, userDependencyDescription, project)
                    break
                } catch (InvalidUserDataException e) {
                    // ignore
                }
            }
        }

        if (!dependency) {throw new InvalidUserDataException("The dependency notation: $userDependencyDescription is invalid!")}
        dependency.initialize()
        dependency
    }
}
