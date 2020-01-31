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

package org.gradle.api.internal.artifacts.type;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeContainer;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.reflect.Instantiator;

import java.util.Set;

public class DefaultArtifactTypeContainer extends AbstractValidatingNamedDomainObjectContainer<ArtifactTypeDefinition> implements ArtifactTypeContainer {
    private final ImmutableAttributesFactory attributesFactory;

    public DefaultArtifactTypeContainer(Instantiator instantiator, ImmutableAttributesFactory attributesFactory, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(ArtifactTypeDefinition.class, instantiator, callbackActionDecorator);
        this.attributesFactory = attributesFactory;
    }

    @Override
    protected ArtifactTypeDefinition doCreate(final String name) {
        return getInstantiator().newInstance(DefaultArtifactTypeDefinition.class, name, attributesFactory);
    }

    public static class DefaultArtifactTypeDefinition implements ArtifactTypeDefinition {
        private final String name;
        private final AttributeContainer attributes;
        private Set<String> fileNameExtensions;
        private Set<ModuleIdentifier> moduleIdentifiers;

        public DefaultArtifactTypeDefinition(String name, ImmutableAttributesFactory attributesFactory) {
            this.name = name;
            attributes = attributesFactory.mutable();
        }

        @Override
        public void forFileNameExtension(String extension) {
            if (fileNameExtensions == null) {
                fileNameExtensions = Sets.newHashSet();
            }
            fileNameExtensions.add(extension);
        }

        @Override
        public void forModule(String group, String name) {
            if (moduleIdentifiers == null) {
                moduleIdentifiers = Sets.newHashSet();
            }
            moduleIdentifiers.add(DefaultModuleIdentifier.newId(group, name));
        }

        @Override
        public Set<ModuleIdentifier> getModuleIdentifiers() {
            if (moduleIdentifiers == null) {
                return ImmutableSet.of();
            }
            return ImmutableSet.copyOf(moduleIdentifiers);
        }

        @Override
        public Set<String> getFileNameExtensions() {
            if (fileNameExtensions == null) {
                return ImmutableSet.of(name);
            }
            return ImmutableSet.copyOf(fileNameExtensions);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }
    }
}
