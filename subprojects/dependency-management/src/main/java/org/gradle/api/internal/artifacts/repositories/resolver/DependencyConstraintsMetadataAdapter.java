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

import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;

public class DependencyConstraintsMetadataAdapter extends AbstractDependenciesMetadataAdapter<DependencyConstraintMetadata> implements DependencyConstraintsMetadata {

    public DependencyConstraintsMetadataAdapter(ImmutableAttributesFactory attributesFactory,
                                                List<org.gradle.internal.component.model.DependencyMetadata> dependenciesMetadata,
                                                Instantiator instantiator,
                                                NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintsNotationParser) {
        super(attributesFactory, dependenciesMetadata, instantiator, dependencyConstraintsNotationParser);
    }

    @Override
    protected Class<? extends DependencyConstraintMetadata> adapterImplementationType() {
        return DependencyConstraintMetadataAdapter.class;
    }

    @Override
    protected boolean isConstraint() {
        return true;
    }

    @Override
    protected boolean isEndorsingStrictVersions(DependencyConstraintMetadata details) {
        return false;
    }

}
