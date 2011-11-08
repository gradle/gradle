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

package org.gradle.api.internal.artifacts.notations;

import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.notations.NotationParser;

import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/8/11
 */
public class DependencyNotationParser implements NotationParser<Dependency> {

    private final Set<IDependencyImplementationFactory> dependencyFactories;

    //TODO SF - relax the constructor
    public DependencyNotationParser(Set<IDependencyImplementationFactory> dependencyFactories) {
        this.dependencyFactories = dependencyFactories;
    }

    public Dependency parseNotation(Object dependencyNotation) {
        if (dependencyNotation instanceof Dependency) {
            return (Dependency) dependencyNotation;
        }

        Dependency dependency = null;
        for (IDependencyImplementationFactory factory : dependencyFactories) {
            try {
                dependency = factory.createDependency(Dependency.class, dependencyNotation);
                break;
            } catch (IllegalDependencyNotation e) {
                // ignore
            } catch (Exception e) {
                throw new GradleException(String.format("Could not create a dependency using notation: %s", dependencyNotation), e);
            }
        }

        if (dependency == null) {
            throw new InvalidUserDataException(String.format("The dependency notation: %s is invalid.",
                    dependencyNotation));
        }
        return dependency;
    }
}
