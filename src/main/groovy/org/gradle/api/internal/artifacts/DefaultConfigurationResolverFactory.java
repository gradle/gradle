/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationResolver;
import org.gradle.api.artifacts.ResolverContainer;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationResolverFactory implements ConfigurationResolverFactory {
    private IvyService ivyService;
    private File gradleUserHome;

    public DefaultConfigurationResolverFactory(IvyService ivyService, File gradleUserHome) {
        this.ivyService = ivyService;
        this.gradleUserHome = gradleUserHome;
    }

    public ConfigurationResolver createConfigurationResolver(Configuration configuration, DependencyContainerInternal dependencyContainer, ResolverContainer resolverContainer,
                                                             ArtifactContainer artifactContainer, ConfigurationContainer publishConfigurationContainer) {
        return new DefaultConfigurationResolver(configuration, dependencyContainer, artifactContainer, publishConfigurationContainer,
                resolverContainer, ivyService, gradleUserHome);
    }

    public IvyService getIvyHandler() {
        return ivyService;
    }

    public File getGradleUserHome() {
        return gradleUserHome;
    }
}
