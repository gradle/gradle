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

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.transport.http.HttpResponseResource;
import org.gradle.internal.UncheckedException;
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

    PluginUseMetaData queryPluginMetadata(PluginRequest pluginRequest, String portalUrl) {
        URI portalUri = toUri(portalUrl, "plugin portal");
        RepositoryTransport transport = transportFactory.createTransport(ImmutableSet.of(portalUri.getScheme()), "Plugin Portal", new DefaultPasswordCredentials());
        String requestUrl = String.format(portalUrl + REQUEST_URL, GradleVersion.current().getVersion(), pluginRequest.getId(), pluginRequest.getVersion());
        URI requestUri = toUri(requestUrl, "plugin request");

        ExternalResource resource = null;
        try {
            resource = transport.getRepository().getResource(requestUri);
            HttpResponseResource response = (HttpResponseResource) resource;
            if (response.getStatusCode() != 200) {
                throw new UncheckedIOException(String.format("Failed to resolve plugin %s:%s from portal %s. HTTP status code: %d",
                        pluginRequest.getId(), pluginRequest.getVersion(), portalUrl, response.getStatusCode()));
            }
            return resource.withContent(new Transformer<PluginUseMetaData, InputStream>() {
                public PluginUseMetaData transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }
                    return new Gson().fromJson(reader, PluginUseMetaData.class);
                }
            });
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
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

    private URI toUri(String requestUrl, String kind) {
        try {
            return new URI(requestUrl);
        } catch (URISyntaxException e) {
            throw new InvalidUserDataException(String.format("Invalid %s URL: %s", kind, requestUrl, e));
        }
    }
}
