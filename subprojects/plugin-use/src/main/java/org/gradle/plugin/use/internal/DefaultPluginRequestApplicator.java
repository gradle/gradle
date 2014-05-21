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

package org.gradle.plugin.use.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.util.LinkedList;
import java.util.List;

public class DefaultPluginRequestApplicator implements PluginRequestApplicator {

    private final PluginResolver pluginResolver;
    private final Action<? super PluginResolution> pluginResolutionHandler;

    public DefaultPluginRequestApplicator(PluginResolver pluginResolver, Action<? super PluginResolution> pluginResolutionHandler) {
        this.pluginResolver = pluginResolver;
        this.pluginResolutionHandler = pluginResolutionHandler;
    }

    public void applyPlugin(PluginRequest request) {
        DefaultPluginResolutionResult result = new DefaultPluginResolutionResult();
        try {
            pluginResolver.resolve(request, result);
        } catch (Exception e) {
            throw new LocationAwareException(
                    new GradleException(String.format("Error resolving plugin %s.", request.getDisplayName()), e),
                    request.getScriptSource(), request.getLineNumber());
        }
        if (result.isFound()) {
            pluginResolutionHandler.execute(result.found.resolution);
        } else {
            String message = buildNotFoundMessage(request, result);
            Exception exception = new UnknownPluginException(message);
            throw new LocationAwareException(exception, request.getScriptSource(), request.getLineNumber());
        }
    }

    private String buildNotFoundMessage(PluginRequest pluginRequest, DefaultPluginResolutionResult result) {
        if (result.notFoundList.isEmpty()) {
            // this shouldn't happen, resolvers should call notFound()
            return String.format("Plugin %s was not found", pluginRequest.getDisplayName());
        } else {
            StringBuilder sb = new StringBuilder("Plugin ")
                    .append(pluginRequest.getDisplayName())
                    .append(" was not found in any of the following sources:\n");

            for (NotFound notFound : result.notFoundList) {
                sb.append('\n').append("- ").append(notFound.source);
                if (notFound.detail != null) {
                    sb.append(" (").append(notFound.detail).append(")");
                }
            }

            return sb.toString();
        }
    }

    private static class NotFound {
        private final String source;
        private final String detail;

        private NotFound(String source, String detail) {
            this.source = source;
            this.detail = detail;
        }
    }

    private static class Found {
        private final String source;
        private final PluginResolution resolution;

        private Found(String source, PluginResolution resolution) {
            this.source = source;
            this.resolution = resolution;
        }
    }

    private static class DefaultPluginResolutionResult implements PluginResolutionResult {

        private final List<NotFound> notFoundList = new LinkedList<NotFound>();
        private Found found;

        public void notFound(String sourceDescription, String notFoundDetail) {
            notFoundList.add(new NotFound(sourceDescription, notFoundDetail));
        }

        public void found(String sourceDescription, PluginResolution pluginResolution) {
            found = new Found(sourceDescription, pluginResolution);
        }

        public boolean isFound() {
            return found != null;
        }
    }
}
