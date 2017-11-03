/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;

public class ComponentMetadataDetailsAdapter implements ComponentMetadataDetails {
    private final MutableModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DependencyMetadata> dependencyMetadataNotationParser;

    public ComponentMetadataDetailsAdapter(MutableModuleComponentResolveMetadata metadata, Instantiator instantiator, NotationParser<Object, DependencyMetadata> dependencyMetadataNotationParser) {
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return metadata.getId();
    }

    @Override
    public boolean isChanging() {
        return metadata.isChanging();
    }

    public String getStatus() {
        return metadata.getStatus();
    }

    @Override
    public List<String> getStatusScheme() {
        return metadata.getStatusScheme();
    }

    @Override
    public void setChanging(boolean changing) {
        metadata.setChanging(changing);
    }

    @Override
    public void setStatus(String status) {
        metadata.setStatus(status);
    }

    @Override
    public void setStatusScheme(List<String> statusScheme) {
        metadata.setStatusScheme(statusScheme);
    }

    @Override
    public void withVariant(String name, Action<VariantMetadata> action) {
        assertVariantExists(name);
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, name, metadata, instantiator, dependencyMetadataNotationParser));
    }

    private void assertVariantExists(String name) {
        if (!metadata.definesVariant(name)) {
            throw new GradleException("Variant " + name + " is not declared for " + metadata.getId());
        }
    }
}
