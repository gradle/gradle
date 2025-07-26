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

import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationFactory;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.typeconversion.NotationParser;

public final class ConfigurationFactoriesBundle {
    public final CalculatedValueContainerFactory calculatedValueContainerFactory;
    public final ObjectFactory objectFactory;
    public final FileCollectionFactory fileCollectionFactory;
    public final TaskDependencyFactory taskDependencyFactory;
    public final AttributesFactory attributesFactory;
    public final DomainObjectCollectionFactory domainObjectCollectionFactory;
    public final DefaultConfigurationFactory defaultConfigurationFactory;
    public final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    public final NotationParser<Object, Capability> capabilityNotationParser;
    public final CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    public ConfigurationFactoriesBundle(CalculatedValueContainerFactory calculatedValueContainerFactory,
                                        ObjectFactory objectFactory,
                                        FileCollectionFactory fileCollectionFactory,
                                        TaskDependencyFactory taskDependencyFactory,
                                        AttributesFactory attributesFactory,
                                        DomainObjectCollectionFactory domainObjectCollectionFactory,
                                        DefaultConfigurationFactory defaultConfigurationFactory,
                                        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
                                        NotationParser<Object, Capability> capabilityNotationParser,
                                        CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.objectFactory = objectFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.artifactNotationParser = artifactNotationParser;
        this.capabilityNotationParser = capabilityNotationParser;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }
}
