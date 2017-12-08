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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

public class VariantMetadataAdapter implements VariantMetadata {
    private final String name;
    private final MutableModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser;

    public VariantMetadataAdapter(String name, MutableModuleComponentResolveMetadata metadata, Instantiator instantiator,
                                  NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser,
                                  NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser) {
        this.name = name;
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
    }

    @Override
    public void withDependencies(Action<DirectDependenciesMetadata> action) {
        metadata.addDependencyMetadataRule(name, action, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);
    }

    @Override
    public void withDependencyConstraints(Action<DependencyConstraintsMetadata> action) {
        metadata.addDependencyConstraintMetadataRule(name, action, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);
    }
}
