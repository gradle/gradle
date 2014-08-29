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

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Actions;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.internal.resource.transport.http.HttpResponseResource;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpPluginResolutionServiceClient implements PluginResolutionServiceClient {
    private static final Escaper PATH_SEGMENT_ESCAPER = UrlEscapers.urlPathSegmentEscaper();
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPluginResolutionServiceClient.class);
    private static final String CLIENT_REQUEST_BASE = String.format("%s", PATH_SEGMENT_ESCAPER.escape(GradleVersion.current().getVersion()));
    private static final String PLUGIN_USE_REQUEST_URL = "/plugin/use/%s/%s";
    private static final String JSON = "application/json";

    public static final String CLIENT_STATUS_CHECKSUM_HEADER = "X-Gradle-Client-Status-Checksum";

    private final HttpResourceAccessor resourceAccessor;

    public HttpPluginResolutionServiceClient(HttpResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Nullable
    public Response<PluginUseMetaData>  queryPluginMetadata(String portalUrl, boolean shouldValidate, final PluginRequest pluginRequest) {
        String escapedId = PATH_SEGMENT_ESCAPER.escape(pluginRequest.getId().toString());
        String escapedPluginVersion = PATH_SEGMENT_ESCAPER.escape(pluginRequest.getVersion());
        final String requestUrl = toRequestUrl(portalUrl, String.format(PLUGIN_USE_REQUEST_URL, escapedId, escapedPluginVersion));
        return request(requestUrl, PluginUseMetaData.class, new Action<PluginUseMetaData>() {
            public void execute(PluginUseMetaData pluginUseMetaData) {
                validate(requestUrl, pluginUseMetaData);
            }
        });
    }

    public Response<ClientStatus> queryClientStatus(String portalUrl, boolean shouldValidate, String checksum) {
        final String requestUrl = toRequestUrl(portalUrl, "");
        return request(requestUrl, ClientStatus.class, Actions.doNothing());
    }

    private String toRequestUrl(String portalUrl, String path) {
        return String.format("%s/%s%s", portalUrl, CLIENT_REQUEST_BASE, path);
    }

    private <T> Response<T> request(final String requestUrl, final Class<T> type, final Action<? super T> validator) {
        final URI requestUri = toUri(requestUrl, "plugin request");

        HttpResponseResource response = null;
        try {
            response = resourceAccessor.getRawResource(requestUri);
            final int statusCode = response.getStatusCode();
            String contentType = response.getContentType();
            if (contentType == null || !contentType.equalsIgnoreCase(JSON)) {
                final String message = String.format("content type is '%s', expected '%s'", contentType == null ? "" : contentType, JSON);
                throw new OutOfProtocolException(requestUrl, message);
            }

            final String clientStatusChecksum = response.getHeaderValue(CLIENT_STATUS_CHECKSUM_HEADER);

            return response.withContent(new Transformer<Response<T>, InputStream>() {
                public Response<T> transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }

                    try {
                        if (statusCode == 200) {
                            T payload = new Gson().fromJson(reader, type);
                            validator.execute(payload);
                            return new SuccessResponse<T>(payload, statusCode, requestUrl, clientStatusChecksum);
                        } else if (statusCode >= 400 && statusCode < 600) {
                            ErrorResponse errorResponse = validate(requestUrl, new Gson().fromJson(reader, ErrorResponse.class));
                            return new ErrorResponseResponse<T>(errorResponse, statusCode, requestUrl, clientStatusChecksum);
                        } else {
                            throw new OutOfProtocolException(requestUrl, "unexpected HTTP response status " + statusCode);
                        }
                    } catch (JsonSyntaxException e) {
                        throw new OutOfProtocolException(requestUrl, "could not parse response JSON", e);
                    } catch (JsonIOException e) {
                        throw new OutOfProtocolException(requestUrl, "could not parse response JSON", e);
                    }
                }
            });
        } catch (IOException e) {
            throw new org.gradle.api.UncheckedIOException(e);
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

    public void close() throws IOException {

    }

    private PluginUseMetaData validate(String url, PluginUseMetaData pluginUseMetaData) {
        if (pluginUseMetaData.implementationType == null) {
            throw new OutOfProtocolException(url, "invalid plugin metadata - no implementation type specified");
        }
        if (!pluginUseMetaData.implementationType.equals(PluginUseMetaData.M2_JAR)) {
            throw new OutOfProtocolException(url, String.format("invalid plugin metadata - unsupported implementation type '%s'", pluginUseMetaData.implementationType));
        }
        if (pluginUseMetaData.implementation == null) {
            throw new OutOfProtocolException(url, "invalid plugin metadata - no implementation specified");
        }
        if (pluginUseMetaData.implementation.get("gav") == null) {
            throw new OutOfProtocolException(url, "invalid plugin metadata - no module coordinates specified");
        }
        if (pluginUseMetaData.implementation.get("repo") == null) {
            throw new OutOfProtocolException(url, "invalid plugin metadata - no module repository specified");
        }

        return pluginUseMetaData;
    }

    private ErrorResponse validate(String url, ErrorResponse errorResponse) {
        if (errorResponse.errorCode == null) {
            throw new OutOfProtocolException(url, "invalid error response - no error code specified");
        }

        if (errorResponse.message == null) {
            throw new OutOfProtocolException(url, "invalid error response - no message specified");
        }

        return errorResponse;
    }

    private URI toUri(String url, String kind) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new GradleException(String.format("Invalid %s URL: %s", kind, url), e);
        }
    }

    private static class OutOfProtocolException extends GradleException {
        private OutOfProtocolException(String requestUrl, String message) {
            super(toMessage(requestUrl, message));
        }

        private OutOfProtocolException(String requestUrl, String message, Throwable cause) {
            super(toMessage(requestUrl, message), cause);
        }

        private static String toMessage(String requestUrl, String message) {
            return String.format("The response from %s was not a valid response from a Gradle Plugin Resolution Service: %s", requestUrl, message);
        }
    }
}
