/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;

import javax.inject.Inject;

public class DefaultJvmPluginExtension implements JvmPluginExtension {
    private final JvmPluginServices services;

    @Inject
    public DefaultJvmPluginExtension(JvmPluginServices services) {
        this.services = services;
    }

    @Override
    public JvmEcosystemUtilities getUtilities() {
        return services;
    }

    @Override
    public Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> configuration) {
        return services.createOutgoingElements(name, configuration);
    }

    @Override
    public Configuration createResolvableConfiguration(String name, Action<? super ResolvableConfigurationBuilder> action) {
        return services.createResolvableConfiguration(name, action);
    }

    @Override
    public void createJvmVariant(String name, Action<? super JvmVariantBuilder> configuration) {
        services.createJvmVariant(name, configuration);
    }
}
