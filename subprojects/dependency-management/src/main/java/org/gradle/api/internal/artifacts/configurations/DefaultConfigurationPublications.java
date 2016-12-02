/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.FactoryNamedDomainObjectContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

public class DefaultConfigurationPublications implements ConfigurationPublications {
    private final PublishArtifactSet artifacts;
    private final FactoryNamedDomainObjectContainer<Variant> variants;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;

    public DefaultConfigurationPublications(PublishArtifactSet artifacts, final AttributeContainerInternal parentAttributes, final Instantiator instantiator, final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser, final FileCollectionFactory fileCollectionFactory) {
        this.artifacts = artifacts;
        variants = new FactoryNamedDomainObjectContainer<Variant>(Variant.class, instantiator, new NamedDomainObjectFactory<Variant>() {
            @Override
            public Variant create(String name) {
                return instantiator.newInstance(DefaultVariant.class, name, parentAttributes, artifactNotationParser, fileCollectionFactory);
            }
        });
        this.artifactNotationParser = artifactNotationParser;
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public void artifact(Object notation) {
        artifacts.add(artifactNotationParser.parseNotation(notation));
    }

    @Override
    public void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
        ConfigurablePublishArtifact publishArtifact = artifactNotationParser.parseNotation(notation);
        artifacts.add(publishArtifact);
        configureAction.execute(publishArtifact);
    }

    @Override
    public NamedDomainObjectContainer<Variant> getVariants() {
        return variants;
    }

    @Override
    public void variants(Action<? super NamedDomainObjectContainer<Variant>> configureAction) {
        configureAction.execute(variants);
    }

}
