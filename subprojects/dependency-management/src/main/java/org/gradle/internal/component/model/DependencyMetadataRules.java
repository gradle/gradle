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
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of rules provided by the build script author (as {@link Action<DependenciesMetadata>}) that
 * are applied on the dependencies defined in variant/configuration metadata.
 * The rules are applied in implementations of {@link ConfigurationMetadata#getDependencies()}.
 */
public class DependencyMetadataRules {
    private final Instantiator instantiator;
    private final NotationParser<Object, DependencyMetadata> dependencyNotationParser;

    private final List<Action<DependenciesMetadata>> actions = new ArrayList<Action<DependenciesMetadata>>();

    public DependencyMetadataRules(Instantiator instantiator, NotationParser<Object, DependencyMetadata> dependencyNotationParser) {
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
    }

    public List<Action<DependenciesMetadata>> getActions() {
        return actions;
    }

    public Instantiator getInstantiator() {
        return instantiator;
    }

    public NotationParser<Object, DependencyMetadata> getDependencyNotationParser() {
        return dependencyNotationParser;
    }
}
