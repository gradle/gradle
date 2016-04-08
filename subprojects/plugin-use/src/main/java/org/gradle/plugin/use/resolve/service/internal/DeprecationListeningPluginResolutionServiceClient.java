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

import org.gradle.api.Action;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DeprecationListeningPluginResolutionServiceClient implements PluginResolutionServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecationListeningPluginResolutionServiceClient.class);

    private final PluginResolutionServiceClient delegate;
    private final Action<? super String> action;

    public DeprecationListeningPluginResolutionServiceClient(PluginResolutionServiceClient delegate) {
        this(delegate, new Action<String>() {
            public void execute(String s) {
                DeprecationLogger.nagUserWith(s);
            }
        });
    }

    DeprecationListeningPluginResolutionServiceClient(PluginResolutionServiceClient delegate, Action<? super String> action) {
        this.delegate = delegate;
        this.action = action;
    }

    public Response<PluginUseMetaData> queryPluginMetadata(String portalUrl, boolean shouldValidate, PluginRequest pluginRequest) {
        Response<PluginUseMetaData> response = delegate.queryPluginMetadata(portalUrl, shouldValidate, pluginRequest);
        String statusChecksum = response.getClientStatusChecksum();
        if (statusChecksum != null) {
            checkForDeprecation(portalUrl, shouldValidate, statusChecksum);
        }
        return response;
    }

    public Response<ClientStatus> queryClientStatus(String portalUrl, boolean shouldValidate, String checksum) {
        return delegate.queryClientStatus(portalUrl, shouldValidate, checksum);
    }

    private void checkForDeprecation(String portalUrl, boolean shouldValidate, String statusChecksum) {
        Response<ClientStatus> response;
        try {
            response = delegate.queryClientStatus(portalUrl, shouldValidate, statusChecksum);
        } catch (Exception e) {
            LOGGER.debug("Exception thrown fetching client status", e);
            return;
        }

        if (response.isError()) {
            LOGGER.warn("Received error response fetching client status from {}: {}", response.getUrl(), response.getErrorResponse());
        } else {
            ClientStatus status = response.getResponse();
            String deprecationMessage = status.getDeprecationMessage();
            if (deprecationMessage != null) {
                String message = toMessage(deprecationMessage, response.getUrl());
                action.execute(message);
            }
        }
    }

    public void close() throws IOException {
        delegate.close();
    }

    public static String toMessage(String deprecationMessage, String responseUrl) {
        return String.format("Plugin resolution service client status service %s reported that this client has been deprecated: %s", responseUrl, deprecationMessage);
    }

}
