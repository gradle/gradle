/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.repository.internal;

import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.plugin.repository.GradlePluginPortal;

public class GradlePluginPortalPluginRepository extends DefaultMavenPluginRepository implements GradlePluginPortal {

    public static final String OVERRIDE_URL_PROPERTY = "org.gradle.internal.plugins.repo.override";

    public GradlePluginPortalPluginRepository(FileResolver fileResolver, DependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme, AuthenticationSupportedInternal authenticationSupportedInternal) {
        super(fileResolver, dependencyResolutionServices, versionSelectorScheme, authenticationSupportedInternal);
        setUrl(getRepositoryUrl());
    }

    private static String getRepositoryUrl() {
        return System.getProperty(OVERRIDE_URL_PROPERTY, "https://plugins.gradle.org/m2");
    }

    @Override
    protected String pluginResolverName() {
        return "Gradle Central Plugin Repository";
    }
}
