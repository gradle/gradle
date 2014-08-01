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
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.internal.resource.transport.http.HttpResponseResource;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

public class PluginResolutionServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginResolutionServiceClient.class);
    private static final String REQUEST_URL = "/api/gradle/%s/plugin/use/%s/%s";
    private static final String JSON = "application/json";

    private final HttpResourceAccessor resourceAccessor;

    public PluginResolutionServiceClient(HttpResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Nullable
    Response<PluginUseMetaData> queryPluginMetadata(final PluginRequest pluginRequest, String portalUrl) {
        Escaper escaper = UrlEscapers.urlPathSegmentEscaper();
        String escapedId = escaper.escape(pluginRequest.getId().toString());
        String escapedPluginVersion = escaper.escape(pluginRequest.getVersion());
        String escapedGradleVersion = escaper.escape(GradleVersion.current().getVersion());

        final String requestUrl = String.format(portalUrl + REQUEST_URL, escapedGradleVersion, escapedId, escapedPluginVersion);
        final URI requestUri = toUri(requestUrl, "plugin request");

        HttpResponseResource response = null;
        try {
            response = resourceAccessor.getRawResource(requestUri);
            final int statusCode = response.getStatusCode();
            if (!response.getContentType().equalsIgnoreCase(JSON)) {
                final String message = String.format("content type is '%s', expected '%s'", response.getContentType(), JSON);
                throw new OutOfProtocolException(requestUrl, message);
            }

            return response.withContent(new Transformer<Response<PluginUseMetaData>, InputStream>() {
                public Response<PluginUseMetaData> transform(InputStream inputStream) {
                    Reader reader;
                    try {
                        reader = new InputStreamReader(inputStream, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new AssertionError(e);
                    }
                    try {
                        if (statusCode == 200) {
                            PluginUseMetaData metadata = validate(requestUrl, new Gson().fromJson(reader, PluginUseMetaData.class));
                            return new SuccessResponse<PluginUseMetaData>(metadata, statusCode, requestUrl);
                        } else if (statusCode >= 400 && statusCode < 600) {
                            ErrorResponse errorResponse = validate(requestUrl, new Gson().fromJson(reader, ErrorResponse.class));
                            return new ErrorResponseResponse<PluginUseMetaData>(errorResponse, statusCode, requestUrl);
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

    public static interface Response<T> {
        boolean isError();

        int getStatusCode();

        ErrorResponse getErrorResponse();

        T getResponse();

        String getUrl();
    }

    private static class ErrorResponseResponse<T> implements Response<T> {
        private final ErrorResponse errorResponse;
        private final int statusCode;
        private final String url;

        private ErrorResponseResponse(ErrorResponse errorResponse, int statusCode, String url) {
            this.errorResponse = errorResponse;
            this.statusCode = statusCode;
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public boolean isError() {
            return true;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public ErrorResponse getErrorResponse() {
            return errorResponse;
        }

        public T getResponse() {
            return null;
        }
    }

    private static class SuccessResponse<T> implements Response<T> {
        private final T response;
        private final int statusCode;
        private final String url;

        private SuccessResponse(T response, int statusCode, String url) {
            this.response = response;
            this.statusCode = statusCode;
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public boolean isError() {
            return false;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public ErrorResponse getErrorResponse() {
            return null;
        }

        public T getResponse() {
            return response;
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
