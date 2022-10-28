/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.java.usagecontext;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.component.IvyPublishingAwareContext;
import org.gradle.api.internal.component.MavenPublishingAwareContext;
import org.gradle.api.plugins.internal.AbstractConfigurationUsageContext;

public class FeatureConfigurationUsageContext extends AbstractConfigurationUsageContext implements MavenPublishingAwareContext, IvyPublishingAwareContext {
    private final Configuration configuration;
    private final ScopeMapping scopeMapping;
    private final boolean optional;

    public FeatureConfigurationUsageContext(String name, Configuration configuration, ConfigurationVariant variant, String mavenScope, boolean optional) {
        super(name, ((AttributeContainerInternal)variant.getAttributes()).asImmutable(), variant.getArtifacts());
        this.configuration = configuration;
        this.scopeMapping = ScopeMapping.of(mavenScope, optional);
        this.optional = optional;
    }

    @Override
    protected Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public ScopeMapping getScopeMapping() {
        return scopeMapping;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }
}
