/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.util.Formatter;
import java.util.List;

/**
 * Default implementation of {@link PluginResolutionResult}.
 */
public class PluginResolutionResult {

    private final PluginResolution found;
    private final ImmutableList<NotFound> notFoundList;

    public PluginResolutionResult(@Nullable PluginResolution found, ImmutableList<NotFound> notFoundList) {
        this.found = found;
        this.notFoundList = notFoundList;
    }

    /**
     * Record that the plugin was not found in some source of plugins.
     */
    public static PluginResolutionResult notFound() {
        return new PluginResolutionResult(null, ImmutableList.of());
    }

    /**
     * Record that the plugin was not found in some source of plugins.
     *
     * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
     * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
     */
    public static PluginResolutionResult notFound(String sourceDescription, String notFoundMessage) {
        return new PluginResolutionResult(null, ImmutableList.of(new NotFound(sourceDescription, notFoundMessage, null)));
    }

    /**
     * Record that the plugin was not found in some source of plugins.
     *
     * @param sourceDescription a description of the source of plugins, where the plugin requested could not be found
     * @param notFoundMessage message on why the plugin couldn't be found (e.g. it might be available by a different version)
     * @param notFoundDetail detail on how the plugin couldn't be found (e.g. searched locations)
     */
    public static PluginResolutionResult notFound(String sourceDescription, String notFoundMessage, String notFoundDetail) {
        return new PluginResolutionResult(null, ImmutableList.of(new NotFound(sourceDescription, notFoundMessage, notFoundDetail)));
    }
    /**
     * Record that the plugin was not found in multiple sources of plugins
     */
    public static PluginResolutionResult notFound(ImmutableList<NotFound> notFoundList) {
        return new PluginResolutionResult(null, notFoundList);
    }

    /**
     * Record that the plugin was found in some source of plugins.
     *
     * @param pluginResolution the plugin resolution
     */
    public static PluginResolutionResult found(PluginResolution pluginResolution) {
        return new PluginResolutionResult(pluginResolution, ImmutableList.of());
    }

    public boolean isFound() {
        return found != null;
    }

    /**
     * Get the resolved plugin.
     *
     * @param request The original request. Only used for error reporting.
     *
     * @throws RuntimeException if the plugin was not found.
     */
    public PluginResolution getFound(PluginRequestInternal request) {
        if (found != null) {
            return found;
        }

        Formatter sb = new Formatter();
        sb.format("Plugin %s was not found in any of the following sources:%n", request.getDisplayName());

        for (NotFound notFound : notFoundList) {
            sb.format("%n- %s (%s)", notFound.source, notFound.message);
            if (notFound.detail != null) {
                sb.format("%n%s", TextUtil.indent(notFound.detail, "  "));
            }
        }

        String message = sb.toString();
        Exception exception = new UnknownPluginException(message);
        throw new LocationAwareException(exception, request.getScriptDisplayName(), request.getLineNumber());
    }

    public List<NotFound> getNotFound() {
        assert !isFound();
        return notFoundList;
    }

    public static class NotFound {
        private final String source;
        private final String message;
        private final String detail;

        private NotFound(String source, String message, @Nullable String detail) {
            this.source = source;
            this.message = message;
            this.detail = detail;
        }
    }
}
