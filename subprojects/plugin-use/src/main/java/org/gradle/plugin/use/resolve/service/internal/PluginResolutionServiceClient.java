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
import org.apache.commons.io.IOUtils;
import org.gradle.api.*;
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

public class PluginResolutionServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginResolutionServiceClient.class);
    private static final String REQUEST_URL = "/api/gradle/%s/plugin/use/%s/%s";

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
            if (!response.getContentType().equalsIgnoreCase("application/json")) {
                final String message = String.format("Response from '%s' was not a valid plugin resolution service response (returned content type '%s', not 'application/json')", requestUri, response.getContentType());
                if (LOGGER.isInfoEnabled()) {
                    response.withContent(new Action<InputStream>() {
                        public void execute(InputStream inputStream) {
                            try {
                                String content = IOUtils.toString(inputStream, "utf8"); // might not be UTF8, but good enough
                                LOGGER.info("{}, content:\n{}", message, content);
                            } catch (IOException e) {
                                LOGGER.info(String.format("exception raised while trying to log response from %s", requestUri), e);
                            }
                        }
                    });
                }
                throw new GradleException(message);
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
                            PluginUseMetaData metadata = new Gson().fromJson(reader, PluginUseMetaData.class);
                            metadata.verify();
                            return new SuccessResponse<PluginUseMetaData>(metadata, statusCode);
                        } else if (statusCode >= 400 && statusCode < 600) {
                            ErrorResponse errorResponse = new Gson().fromJson(reader, ErrorResponse.class);
                            return new ErrorResponseResponse<PluginUseMetaData>(errorResponse, statusCode);
                        } else {
                            throw new GradleException("Received unexpected HTTP response status " + statusCode + " from " + requestUrl);
                        }
                    } catch (JsonSyntaxException e) {
                        throw new GradleException("Failed to parse plugin resolution service JSON response.", e);
                    } catch (JsonIOException e) {
                        throw new GradleException("Failed to read plugin resolution service JSON response.", e);
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
            throw new GradleException(String.format("Invalid %s URL: %s", kind, url), e);
        }
    }

    public static interface Response<T> {
        boolean isError();

        int getStatusCode();

        ErrorResponse getErrorResponse();

        T getResponse();
    }

    private static class ErrorResponseResponse<T> implements Response<T> {
        private final ErrorResponse errorResponse;
        private final int statusCode;

        private ErrorResponseResponse(ErrorResponse errorResponse, int statusCode) {
            this.errorResponse = errorResponse;
            this.statusCode = statusCode;
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

        private SuccessResponse(T response, int statusCode) {
            this.response = response;
            this.statusCode = statusCode;
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
}
