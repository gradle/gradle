/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

public class CustomRepositoryPluginResolver implements PluginResolver {
    private static final String REPO_SYSTEM_PROPERTY = "org.gradle.plugin.repoUrl";
    private static final String UNSET_REPO_SYSTEM_PROPERTY = "repo-url-unset-in-system-properties";

    private final ClassLoaderScope parentScope;
    private final VersionSelectorScheme versionSelectorScheme;
    private final PluginInspector pluginInspector;
    private final PluginClassPathResolver pluginClassPathResolver;
    private String repoUrl;

    public CustomRepositoryPluginResolver(ClassLoaderScope parentScope, VersionSelectorScheme versionSelectorScheme, PluginInspector pluginInspector, PluginClassPathResolver pluginClassPathResolver) {
        this.parentScope = parentScope;
        this.versionSelectorScheme = versionSelectorScheme;
        this.pluginInspector = pluginInspector;
        this.pluginClassPathResolver = pluginClassPathResolver;
    }

    @Override
    public void resolve(PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (getRepoUrl().equals(UNSET_REPO_SYSTEM_PROPERTY)) {
            return;
        }
        if (pluginRequest.getVersion() == null) {
            result.notFound(getDescription(), "plugin dependency must include a version number for this source");
            return;
        }
        if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
            result.notFound(getDescription(), "snapshot plugin versions are not supported");
            return;
        }
        if (versionSelectorScheme.parseSelector(pluginRequest.getVersion()).isDynamic()) {
            result.notFound(getDescription(), "dynamic plugin versions are not supported");
            return;
        }
        final String artifactNotation = pluginRequest.getId() + ":" + pluginRequest.getId() + ":" + pluginRequest.getVersion();
        try {
            ClassPath classPath = pluginClassPathResolver.resolvePluginDependencies(getRepoUrl(), artifactNotation);
            result.found(getDescription(), new ClassPathPluginResolution(pluginRequest.getId(), parentScope, Factories.constant(classPath), pluginInspector));
        } catch (PluginClassPathResolver.DependencyResolutionException e) {
            result.notFound(getDescription(), String.format("Could not resolve plugin artifact '%s'", artifactNotation));
        }
    }

    // Caches the repoUrl so that we create minimal lock contention on System.getProperty() calls.
    private String getRepoUrl() {
        if (repoUrl == null) {
            repoUrl = System.getProperty(REPO_SYSTEM_PROPERTY, UNSET_REPO_SYSTEM_PROPERTY);
        }
        return repoUrl;
    }

    public static String getDescription() {
        return "User-defined Plugin Repository";
    }
}
