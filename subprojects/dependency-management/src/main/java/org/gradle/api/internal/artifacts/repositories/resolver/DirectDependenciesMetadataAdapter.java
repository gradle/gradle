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

import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

public class DirectDependenciesMetadataAdapter extends AbstractDependenciesMetadataAdapter<DirectDependencyMetadata, DirectDependencyMetadataAdapter> implements DirectDependenciesMetadata {
    public DirectDependenciesMetadataAdapter(ImmutableAttributesFactory attributesFactory,
                                             Instantiator instantiator,
                                             NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser) {
        super(attributesFactory, instantiator, dependencyNotationParser);
    }

    @Override
    protected Class<DirectDependencyMetadataAdapter> adapterImplementationType() {
        return DirectDependencyMetadataAdapter.class;
    }

    @Override
    protected ModuleDependencyMetadata getAdapterMetadata(DirectDependencyMetadataAdapter adapter) {
        return adapter.getMetadata();
    }

    @Override
    protected boolean isConstraint() {
        return false;
    }

    @Override
    protected boolean isEndorsingStrictVersions(DirectDependencyMetadata details) {
        return details.isEndorsingStrictVersions();
    }

}
