/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;

/**
 * A concrete dependency scope {@link DefaultConfiguration} that cannot change roles.
 */
public class DefaultDependencyScopeConfiguration extends DefaultConfiguration implements DependencyScopeConfiguration {

    @Inject
    public DefaultDependencyScopeConfiguration(
        ConfigurationServicesBundle configurationServices,
        DomainObjectContext domainObjectContext,
        String name,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        UserCodeApplicationContext userCodeApplicationContext,
        DefaultConfigurationFactory defaultConfigurationFactory
    ) {
        super(
            configurationServices,
            domainObjectContext,
            name,
            false,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            artifactNotationParser,
            capabilityNotationParser,
            userCodeApplicationContext,
            defaultConfigurationFactory,
            ConfigurationRoles.DEPENDENCY_SCOPE,
            true
        );
    }
}
