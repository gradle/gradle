/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

public class PluginResolutionServiceResolver implements PluginResolver {

    public static final String OVERRIDE_URL_PROPERTY = PluginResolutionServiceResolver.class.getName() + ".repo.override";
    private static final String DEFAULT_API_URL = "https://plugins.gradle.org/api/gradle";

    private final PluginResolutionServiceClient portalClient;
    private final VersionSelectorScheme versionSelectorScheme;
    private final StartParameter startParameter;

    public PluginResolutionServiceResolver(
        PluginResolutionServiceClient portalClient,
        VersionSelectorScheme versionSelectorScheme, StartParameter startParameter
    ) {
        this.portalClient = portalClient;
        this.versionSelectorScheme = versionSelectorScheme;
        this.startParameter = startParameter;
    }

    private static String getUrl() {
        return System.getProperty(OVERRIDE_URL_PROPERTY, DEFAULT_API_URL);
    }

    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest.getVersion() == null) {
            result.notFound(getDescription(), "plugin dependency must include a version number for this source");
            return;
        }
        if (pluginRequest.getVersion().endsWith("-SNAPSHOT")) {
            result.notFound(getDescription(), "snapshot plugin versions are not supported");
        } else if (isDynamicVersion(pluginRequest.getVersion())) {
            result.notFound(getDescription(), "dynamic plugin versions are not supported");
        } else {
            HttpPluginResolutionServiceClient.Response<PluginUseMetaData> response = portalClient.queryPluginMetadata(getUrl(), startParameter.isRefreshDependencies(), pluginRequest);
            if (response.isError()) {
                ErrorResponse errorResponse = response.getErrorResponse();
                if (response.getStatusCode() == 404) {
                    result.notFound(getDescription(), errorResponse.message);
                } else {
                    throw new GradleException(String.format("Plugin resolution service returned HTTP %d with message '%s' (url: %s)", response.getStatusCode(), errorResponse.message, response.getUrl()));
                }
            } else {
                handleLegacy(response.getResponse(), result, pluginRequest);
            }
        }
    }

    private void handleLegacy(final PluginUseMetaData metadata, PluginResolutionResult result, final PluginRequest pluginRequest) {
        final PluginId pluginId = DefaultPluginId.of(metadata.id);
        result.found(getDescription(), new PluginResolution() {
            @Override
            public PluginId getPluginId() {
                return pluginId;
            }

            public void execute(PluginResolveContext context) {
                context.addLegacy(pluginId, metadata.implementation.get("repo"), getGav(pluginRequest, metadata));
            }
        });
    }

    private boolean isDynamicVersion(String version) {
        return versionSelectorScheme.parseSelector(version).isDynamic();
    }

    private Object getGav(PluginRequest request, PluginUseMetaData metadata) {
        if (request.getModule() == null) {
            return metadata.implementation.get("gav");
        } else {
            return request.getModule().toString();
        }
    }

    public String getDescription() {
        return "Gradle Central Plugin Repository";
    }
}
