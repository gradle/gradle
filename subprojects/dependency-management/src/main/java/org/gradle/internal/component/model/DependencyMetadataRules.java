/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.component.model;


import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.DependenciesMetadataAdapter;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of rules provided by the build script author (as {@link Action<DependenciesMetadata>}) that
 * are applied on the dependencies defined in variant/configuration metadata. The rules are applied
 * in the {@link #execute(List)} method when the dependencies of a variant are needed during dependency resolution.
 */
public class DependencyMetadataRules {
    private final Instantiator instantiator;
    private final NotationParser<Object, org.gradle.api.artifacts.DependencyMetadata> dependencyNotationParser;

    private final List<Action<DependenciesMetadata>> actions = new ArrayList<Action<DependenciesMetadata>>();

    public DependencyMetadataRules(Instantiator instantiator, NotationParser<Object, org.gradle.api.artifacts.DependencyMetadata> dependencyNotationParser) {
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
    }

    public void addAction(Action<DependenciesMetadata> action) {
        actions.add(action);
    }

    public <T extends ModuleDependencyMetadata> List<T> execute(List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<T>(dependencies);
        for (Action<DependenciesMetadata> dependenciesMetadataAction : actions) {
            dependenciesMetadataAction.execute(instantiator.newInstance(
                DependenciesMetadataAdapter.class, calculatedDependencies, instantiator, dependencyNotationParser));
        }
        return calculatedDependencies;
    }
}
