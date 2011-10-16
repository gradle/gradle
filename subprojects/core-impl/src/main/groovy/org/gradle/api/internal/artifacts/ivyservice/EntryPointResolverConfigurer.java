/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ForcedVersion;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;

import java.util.Set;

/**
 * entry point resolver is global to all configurations, however it needs
 * specific data from configuration per resolve.
 * <p>
 * by Szczepan Faber, created at: 10/8/11
 */
public class EntryPointResolverConfigurer {
    public void configureResolver(EntryPointResolver resolver, ConfigurationInternal configuration) {
        Set<ForcedVersion> forcedVersions = configuration.getResolutionStrategy().getForcedVersions();
        resolver.setIvyResolutionListener(new MaybeForceVersions(forcedVersions));
    }

}
