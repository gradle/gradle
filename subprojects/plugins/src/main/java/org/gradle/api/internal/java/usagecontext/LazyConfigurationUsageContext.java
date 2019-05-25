/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.plugins.internal.AbstractConfigurationUsageContext;

import java.util.Set;

public class LazyConfigurationUsageContext extends AbstractConfigurationUsageContext {
    private final String configurationName;
    private final ConfigurationContainer configurations;

    public LazyConfigurationUsageContext(String name,
                                         String configurationName,
                                         Set<PublishArtifact> artifacts,
                                         ConfigurationContainer configurations,
                                         ImmutableAttributes attributes) {
        super(name, attributes, artifacts);
        this.configurationName = configurationName;
        this.configurations = configurations;
    }

    @Override
    protected Configuration getConfiguration() {
        return configurations.getByName(configurationName);
    }
}
