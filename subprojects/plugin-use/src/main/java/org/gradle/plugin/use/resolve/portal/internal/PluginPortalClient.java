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

package org.gradle.plugin.use.resolve.portal.internal;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.internal.resource.transport.http.HttpResponseResource;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

public class PluginPortalClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginPortalClient.class);
    private static final String REQUEST_URL = "/api/gradle/%s/plugin/use/%s/%s";

    private final HttpResourceAccessor resourceAccessor;

    public PluginPortalClient(HttpResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Nullable
    PluginUseMetaData queryPluginMetadata(final PluginRequest pluginRequest, String portalUrl) {
        String requestUrl = String.format(portalUrl + REQUEST_URL, GradleVersion.current().getVersion(), pluginRequest.getId(), pluginRequest.getVersion());
        URI requestUri = toUri(requestUrl, "plugin request");

        HttpResponseResource response = null;
        try {
            response = resourceAccessor.getResource(requestUri);
            if (response == null) { // 404
                return null;
            }
            if (response.getStatusCode() != 200) {
                throw new GradleException("Plugin portal returned HTTP status code: " + response.getStatusCode());
            }
            return response.withContent(new Transformer<PluginUseMetaData, InputStream>() {
                public PluginUseMetaData transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        PluginUseMetaData metadata = new Gson().fromJson(reader, PluginUseMetaData.class);
                        metadata.verify();
                        return metadata;
                    } catch (JsonSyntaxException e) {
                        throw new GradleException("Failed to parse plugin portal JSON response.", e);
                    } catch (JsonIOException e) {
                        throw new GradleException("Failed to read plugin portal JSON response.", e);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Error closing HTTP resource", e);
            }
        }
    }

    private URI toUri(String url, String kind) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new GradleException(String.format("Invalid %s URL: %s", kind, url, e));
        }
    }
}
