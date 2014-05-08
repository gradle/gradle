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

package org.gradle.api.internal.artifacts.portal;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.transport.http.HttpResponseResource;
import org.gradle.plugin.resolve.internal.FailedPluginRequestException;
import org.gradle.plugin.resolve.internal.InvalidPluginRequestException;
import org.gradle.plugin.resolve.internal.PluginRequest;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

class PluginPortalClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginPortalClient.class);
    private static final String REQUEST_URL = "/api/gradle/%s/plugin/use/%s/%s";

    private final RepositoryTransportFactory transportFactory;

    PluginPortalClient(RepositoryTransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    @Nullable
    PluginUseMetaData queryPluginMetadata(final PluginRequest pluginRequest, String portalUrl) {
        URI portalUri = toUri(portalUrl, "plugin portal", pluginRequest);
        RepositoryTransport transport = transportFactory.createTransport(portalUri.getScheme(), "Plugin Portal", new DefaultPasswordCredentials());
        String requestUrl = String.format(portalUrl + REQUEST_URL, GradleVersion.current().getVersion(), pluginRequest.getId(), pluginRequest.getVersion());
        URI requestUri = toUri(requestUrl, "plugin request", pluginRequest);

        ExternalResource resource = null;
        try {
            resource = transport.getRepository().getResource(requestUri);
            HttpResponseResource response = (HttpResponseResource) resource;
            if (response == null) { // 404
                return null;
            }
            if (response.getStatusCode() != 200) {
                throw new FailedPluginRequestException(pluginRequest, "Received HTTP status code: " + response.getStatusCode());
            }
            return resource.withContent(new Transformer<PluginUseMetaData, InputStream>() {
                public PluginUseMetaData transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        return new Gson().fromJson(reader, PluginUseMetaData.class);
                    } catch (JsonSyntaxException e) {
                        throw new FailedPluginRequestException(pluginRequest, "Failed to parse plugin portal JSON response.", e);
                    } catch (JsonIOException e) {
                        throw new FailedPluginRequestException(pluginRequest, "Failed to read plugin portal JSON response.", e);
                    }
                }
            });
        } catch (IOException e) {
            throw new FailedPluginRequestException(pluginRequest, "IO error.", e);
        } catch (Exception e) {
            throw new FailedPluginRequestException(pluginRequest, "Unexpected error.", e);
        } finally {
            try {
                if (resource != null) {
                    resource.close();
                }
            } catch (IOException e) {
                LOGGER.warn("Error closing HTTP resource", e);
            }
        }
    }

    private URI toUri(String url, String kind, PluginRequest pluginRequest) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidPluginRequestException(pluginRequest, String.format("Invalid %s URL: %s", kind, url, e));
        }
    }
}
