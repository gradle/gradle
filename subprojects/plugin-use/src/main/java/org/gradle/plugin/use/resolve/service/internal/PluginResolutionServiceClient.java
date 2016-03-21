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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.plugin.use.internal.PluginRequest;

import java.io.Closeable;

/**
 * A client for a Gradle Plugin Resolution web service.
 * <p>
 * Methods of this interface take a {@code portalUrl} parameter.
 * This should be the base of the web service address space, without the Gradle version number component.
 * So, for the public instance this should be {@code "https://plugins.gradle.org/api"}.
 * <p>
 * Methods of this interface take a {@code shouldValidate} parameter.
 * When this parameter is true, implementations SHOULD ensure that the returned data is up to date.
 * That is, cached responses should not be used.
 * However, implementations can choose to ignore this.
 * As such, callers should not always expect this to be honoured.
 */
@ThreadSafe
public interface PluginResolutionServiceClient extends Closeable {

    /**
     * Fetch information about a particular plugin at a particular version.
     * <p>
     * This maps to the {@code /«gradle version»/plugin/use/«id»/«version»} service.
     *
     * @param portalUrl the base url of the web service
     * @param shouldValidate whether cached information should be validated
     * @param pluginRequest the plugin identity and version
     * @return the plugin data
     */
    Response<PluginUseMetaData> queryPluginMetadata(String portalUrl, boolean shouldValidate, PluginRequest pluginRequest);

    /**
     * Fetch status information about the current client.
     * <p>
     * This maps to the {@code /«gradle version»} service.
     * <p>
     * The {@code checksum} parameter can be used as a hint for the suitability of a cached response.
     * If {@code shouldValidate} is false and the client has cached response with the given checksum, the cached response may be returned.
     * This checksum value is provided by {@link PluginResolutionServiceClient.Response#getClientStatusChecksum()} of all responses returned by this interface.
     * If the checksum value is not known, pass {@code null} which will force a refresh of the status.
     *
     * @param portalUrl the base url of the web service
     * @param shouldValidate whether cached information should be validated
     * @param checksum the latest checksum value for the status if known, otherwise {@code null}
     * @return the client status
     */
    Response<ClientStatus> queryClientStatus(String portalUrl, boolean shouldValidate, @Nullable String checksum);

    public static interface Response<T> {
        boolean isError();

        int getStatusCode();

        ErrorResponse getErrorResponse();

        T getResponse();

        String getUrl();

        @Nullable
        String getClientStatusChecksum();
    }

    class ErrorResponseResponse<T> implements Response<T> {
        private final ErrorResponse errorResponse;
        private final int statusCode;
        private final String url;
        private final String clientStatusChecksum;

        public ErrorResponseResponse(ErrorResponse errorResponse, int statusCode, String url, String clientStatusChecksum) {
            this.errorResponse = errorResponse;
            this.statusCode = statusCode;
            this.url = url;
            this.clientStatusChecksum = clientStatusChecksum;
        }

        public String getUrl() {
            return url;
        }

        public String getClientStatusChecksum() {
            return clientStatusChecksum;
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

    class SuccessResponse<T> implements Response<T> {
        private final T response;
        private final int statusCode;
        private final String url;
        private final String clientStatusChecksum;

        public SuccessResponse(T response, int statusCode, String url, String clientStatusChecksum) {
            this.response = response;
            this.statusCode = statusCode;
            this.url = url;
            this.clientStatusChecksum = clientStatusChecksum;
        }

        public String getUrl() {
            return url;
        }

        public String getClientStatusChecksum() {
            return clientStatusChecksum;
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
