/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationPublications;
import org.gradle.api.internal.artifacts.configurations.PublishArtifactSetProvider;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

public final class ConsumableState {
    public final DefaultPublishArtifactSet artifacts;
    public final DefaultDomainObjectSet<PublishArtifact> ownArtifacts;
    public @Nullable DefaultPublishArtifactSet allArtifacts;
    public final DefaultConfigurationPublications outgoing;

    public ConsumableState(ConfigurationFactoriesBundle sharedFactories,
                           DisplayName displayName,
                           DefaultDomainObjectSet<PublishArtifact> ownArtifacts,
                           Action<String> ownArtifactsMutationValidator,
                           AttributeContainerInternal configurationAttributes,
                           PublishArtifactSetProvider publishArtifactSetProvider
    ) {
        this.ownArtifacts = ownArtifacts;
        this.ownArtifacts.beforeCollectionChanges(ownArtifactsMutationValidator);

        this.artifacts = new DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, sharedFactories.fileCollectionFactory, sharedFactories.taskDependencyFactory);

        this.outgoing = sharedFactories.objectFactory.newInstance(DefaultConfigurationPublications.class, displayName, artifacts, publishArtifactSetProvider, configurationAttributes, sharedFactories.artifactNotationParser, sharedFactories.capabilityNotationParser, sharedFactories.fileCollectionFactory, sharedFactories.attributesFactory, sharedFactories.domainObjectCollectionFactory, sharedFactories.taskDependencyFactory);
    }
}
