/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state;

import org.gradle.api.Action;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationPublications;
import org.gradle.api.internal.artifacts.configurations.PublishArtifactSetProvider;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParser;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.typeconversion.NotationParser;
import org.jspecify.annotations.Nullable;

/**
 * The state of a configuration that is only needed when it is consumed: its artifacts and outgoing variants.
 */
public final class ConsumableState {
    public final DefaultDomainObjectSet<PublishArtifact> ownArtifacts;
    public final DefaultPublishArtifactSet artifacts;
    public @Nullable DefaultPublishArtifactSet allArtifacts;
    public final DefaultConfigurationPublications outgoing;

    public ConsumableState(
        ConfigurationServicesBundle configurationServices,
        DisplayName displayName,
        Action<String> ownArtifactsMutationValidator,
        AttributeContainerInternal configurationAttributes,
        PublishArtifactSetProvider allArtifactsProvider,
        PublishArtifactNotationParser artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser
    ) {
        this.ownArtifacts = (DefaultDomainObjectSet<PublishArtifact>) configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(PublishArtifact.class);
        this.ownArtifacts.beforeCollectionChanges(ownArtifactsMutationValidator);

        this.artifacts = new DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, configurationServices.getFileCollectionFactory(), configurationServices.getTaskDependencyFactory());

        this.outgoing = configurationServices.getObjectFactory().newInstance(
            DefaultConfigurationPublications.class,
            displayName,
            artifacts,
            allArtifactsProvider,
            configurationAttributes,
            artifactNotationParser,
            capabilityNotationParser,
            configurationServices.getFileCollectionFactory(),
            configurationServices.getAttributesFactory(),
            configurationServices.getDomainObjectCollectionFactory(),
            configurationServices.getTaskDependencyFactory()
        );
    }
}
