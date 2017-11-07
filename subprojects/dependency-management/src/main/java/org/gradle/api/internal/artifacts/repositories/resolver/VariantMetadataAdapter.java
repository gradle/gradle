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
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

public class VariantMetadataAdapter implements VariantMetadata {
    private String name;
    private MutableModuleComponentResolveMetadata metadata;
    private Instantiator instantiator;
    private NotationParser<Object, DependencyMetadata> dependencyMetadataNotationParser;

    public VariantMetadataAdapter(String name, MutableModuleComponentResolveMetadata metadata, Instantiator instantiator, NotationParser<Object, DependencyMetadata> dependencyMetadataNotationParser) {
        this.name = name;
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
    }

    @Override
    public void withDependencies(Action<DependenciesMetadata> action) {
        metadata.addDependencyMetadataRule(name, action, instantiator, dependencyMetadataNotationParser);
    }
}
