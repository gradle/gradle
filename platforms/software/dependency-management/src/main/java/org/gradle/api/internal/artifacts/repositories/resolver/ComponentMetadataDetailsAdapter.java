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

import org.apache.groovy.util.Maps;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;

public class ComponentMetadataDetailsAdapter implements ComponentMetadataDetails {
    private final MutableModuleComponentResolveMetadata metadata;
    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentIdentifierParser;
    private final PlatformSupport platformSupport;

    public ComponentMetadataDetailsAdapter(MutableModuleComponentResolveMetadata metadata, Instantiator instantiator,
                                           NotationParser<Object, DirectDependencyMetadata> dependencyMetadataNotationParser,
                                           NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintMetadataNotationParser,
                                           NotationParser<Object, ComponentIdentifier> dependencyNotationParser,
                                           PlatformSupport platformSupport) {
        this.metadata = metadata;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
        this.componentIdentifierParser = dependencyNotationParser;
        this.platformSupport = platformSupport;
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
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, name, metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void allVariants(Action<? super VariantMetadata> action) {
        action.execute(instantiator.newInstance(VariantMetadataAdapter.class, null, metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser));
    }

    @Override
    public void addVariant(String name, Action<? super VariantMetadata> action) {
        metadata.getVariantMetadataRules().addVariant(name);
        withVariant(name, action);
    }

    @Override
    public void addVariant(String name, String base, Action<? super VariantMetadata> action) {
        metadata.getVariantMetadataRules().addVariant(name, base, false);
        withVariant(name, action);
    }

    @Override
    public void maybeAddVariant(String name, String base, Action<? super VariantMetadata> action) {
        metadata.getVariantMetadataRules().addVariant(name, base, true);
        withVariant(name, action);
    }

    @Override
    public void belongsTo(Object notation) {
        belongsTo(notation, true);
    }

    @Override
    public void belongsTo(Object notation, boolean virtual) {
        ComponentIdentifier id = componentIdentifierParser.parseNotation(notation);
        if (virtual) {
            metadata.belongsTo(VirtualComponentHelper.makeVirtual(id));
        } else if (id instanceof ModuleComponentIdentifier) {
            addPlatformDependencyToAllVariants((ModuleComponentIdentifier) id);
        } else {
            throw new InvalidUserCodeException(notation + " is not a valid platform identifier");
        }
    }

    private void addPlatformDependencyToAllVariants(ModuleComponentIdentifier platformId) {
        allVariants(v -> v.withDependencies(dependencies -> {
            dependencies.add(Maps.of("group", platformId.getGroup(), "name", platformId.getModule(), "version", platformId.getVersion()),
                platformDependency -> platformDependency.attributes(attributes -> attributes.attribute(Category.CATEGORY_ATTRIBUTE, platformSupport.getRegularPlatformCategory())));
        }));
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

}
