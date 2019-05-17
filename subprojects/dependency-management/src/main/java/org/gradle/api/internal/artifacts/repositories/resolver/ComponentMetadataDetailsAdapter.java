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
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;

public class ComponentMetadataDetailsAdapter implements ComponentMetadataDetails {
    private final MutableModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentIdentifierParser;

    public ComponentMetadataDetailsAdapter(MutableModuleComponentResolveMetadata metadata, Instantiator instantiator,
                                           NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser,
                                           NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser,
                                           NotationParser<Object, ComponentIdentifier> dependencyNotationParser) {
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
        this.componentIdentifierParser = dependencyNotationParser;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return metadata.getModuleVersionId();
    }

    @Override
    public boolean isChanging() {
        return metadata.isChanging();
    }

    @Override
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
    public void withVariant(String name, Action<? super VariantMetadata> action) {
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, new VariantNameSpec(name), metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void allVariants(Action<? super VariantMetadata> action) {
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, Specs.satisfyAll(), metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void belongsTo(Object notation) {
        belongsTo(notation, true);
    }

    @Override
    public void belongsTo(Object notation, boolean virtual) {
        ComponentIdentifier id = componentIdentifierParser.parseNotation(notation);
        if (virtual) {
            id = VirtualComponentHelper.makeVirtual(id);
        }
        metadata.belongsTo(id);
    }

    @Override
    public ComponentMetadataDetails attributes(Action<? super AttributeContainer> action) {
        AttributeContainer attributes = metadata.getAttributesFactory().mutable((AttributeContainerInternal) metadata.getAttributes());
        action.execute(attributes);
        metadata.setAttributes(attributes);
        return this;
    }

    @Override
    public AttributeContainer getAttributes() {
        return metadata.getAttributes();
    }

    @Override
    public String toString() {
        return metadata.getModuleVersionId().toString();
    }

    private static class VariantNameSpec implements Spec<VariantResolveMetadata> {
        private final String name;

        private VariantNameSpec(String name) {
            this.name = name;
        }

        @Override
        public boolean isSatisfiedBy(VariantResolveMetadata element) {
            return name.equals(element.getName());
        }
    }

}
