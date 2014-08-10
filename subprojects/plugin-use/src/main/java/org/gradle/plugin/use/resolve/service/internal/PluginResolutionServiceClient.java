/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Nullable;
import org.gradle.plugin.use.internal.PluginRequest;

public interface PluginResolutionServiceClient {
    @Nullable
    Response<PluginUseMetaData> queryPluginMetadata(PluginRequest pluginRequest, String portalUrl);

    public static interface Response<T> {
        boolean isError();

        int getStatusCode();

        ErrorResponse getErrorResponse();

        T getResponse();

        String getUrl();
    }

    public static class ErrorResponseResponse<T> implements Response<T> {
        private final ErrorResponse errorResponse;
        private final int statusCode;
        private final String url;

        public ErrorResponseResponse(ErrorResponse errorResponse, int statusCode, String url) {
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

    public static class SuccessResponse<T> implements Response<T> {
        private final T response;
        private final int statusCode;
        private final String url;

        public SuccessResponse(T response, int statusCode, String url) {
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
}
