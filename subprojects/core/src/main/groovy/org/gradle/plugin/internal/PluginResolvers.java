/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.internal;

import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.JCenterPluginMapper;
import org.gradle.plugin.resolve.internal.JCenterRepositoryConfigurer;
import org.gradle.plugin.resolve.internal.ModuleMappingPluginResolver;
import org.gradle.plugin.resolve.internal.PluginResolver;

public abstract class PluginResolvers {

    public static PluginResolver jcenterGradleOfficial(Instantiator instantiator, DependencyResolutionServices dependencyResolutionServices) {
        final JCenterPluginMapper mapper = new JCenterPluginMapper();
        return new ModuleMappingPluginResolver(
                "jcenter plugin resolver", dependencyResolutionServices, instantiator,
                mapper, new JCenterRepositoryConfigurer()
        ) {
            @Override
            public String getDescriptionForNotFoundMessage() {
                return String.format("Gradle Bintray Plugin Repository (listing: %s)", mapper.getBintrayRepoUrl());
            }
        };
    }
}
