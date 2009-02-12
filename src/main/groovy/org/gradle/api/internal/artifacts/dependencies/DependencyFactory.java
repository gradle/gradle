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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConfigurationMappingContainer;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DependencyFactory implements IDependencyImplementationFactory {
    private Set<IDependencyImplementationFactory> dependencyFactories;

    public DependencyFactory(Set<IDependencyImplementationFactory> dependencyFactories) {
        this.dependencyFactories = dependencyFactories;
    }

    public Dependency createDependency(DependencyConfigurationMappingContainer configurationMappings, Object userDependencyDescription, Project project) {
        Dependency dependency = null;
        for (IDependencyImplementationFactory factory : dependencyFactories) {
            try {
                dependency = factory.createDependency(configurationMappings, userDependencyDescription, project);
                break;
            }
            catch (UnknownDependencyNotation e) {
                // ignore
            }
            catch (Exception e) {
                throw new GradleException("Dependency creation error.", e);
            }
        }

        if (dependency == null) {
            throw new InvalidUserDataException("The dependency notation: " + userDependencyDescription + " is invalid!");
        }
        return dependency;
    }

    public Set<IDependencyImplementationFactory> getDependencyFactories() {
        return dependencyFactories;
    }

    public void setDependencyFactories(Set<IDependencyImplementationFactory> dependencyFactories) {
        this.dependencyFactories = dependencyFactories;
    }
}
