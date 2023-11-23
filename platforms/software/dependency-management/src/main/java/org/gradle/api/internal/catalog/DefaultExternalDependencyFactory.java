/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.catalog;

import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

public class DefaultExternalDependencyFactory extends AbstractExternalDependencyFactory {

    public DefaultExternalDependencyFactory(
        DefaultVersionCatalog config,
        ProviderFactory providers,
        ObjectFactory objects,
        ImmutableAttributesFactory attributesFactory,
        CapabilityNotationParser capabilityNotationParser
    ) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

}
