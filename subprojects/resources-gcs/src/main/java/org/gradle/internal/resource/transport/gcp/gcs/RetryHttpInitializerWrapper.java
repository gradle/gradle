/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.gcp.gcs;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpBackOffIOExceptionHandler;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.Sleeper;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * RetryHttpInitializerWrapper will automatically retry upon RPC failures, preserving the
 * auto-refresh behavior of the Google Credentials.
 *
 * Adapted from https://github.com/GoogleCloudPlatform/java-docs-samples,
 * available under <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0</a> license.
 */
final class RetryHttpInitializerWrapper implements HttpRequestInitializer {

    private static final Logger LOG = Logging.getLogger(RetryHttpInitializerWrapper.class);
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private final Supplier<Credential> credentialSupplier;
    private final Sleeper sleeper;

    RetryHttpInitializerWrapper(Supplier<Credential> credentialSupplier) {
        this(credentialSupplier, Sleeper.DEFAULT);
    }

    RetryHttpInitializerWrapper(Supplier<Credential> credentialSupplier, Sleeper sleeper) {
        this.credentialSupplier = Preconditions.checkNotNull(credentialSupplier);
        this.sleeper = sleeper;
    }

    @Override
    public void initialize(HttpRequest request) {
        // Turn off request logging, this can end up logging OAUTH
        // tokens which should not ever be in a build log
        final boolean loggingEnabled = false;
        request.setLoggingEnabled(loggingEnabled);
        request.setCurlLoggingEnabled(loggingEnabled);
        disableHttpTransportLogging();

        request.setReadTimeout((int) DEFAULT_READ_TIMEOUT_MILLIS);
        final HttpUnsuccessfulResponseHandler backoffHandler =
            new HttpBackOffUnsuccessfulResponseHandler(
                new ExponentialBackOff()).setSleeper(sleeper);
        final Credential credential = credentialSupplier.get();
        request.setInterceptor(credential);
        request.setUnsuccessfulResponseHandler(new HttpUnsuccessfulResponseHandler() {
            @Override
            public boolean handleResponse(HttpRequest request, HttpResponse response, boolean supportsRetry) throws IOException {
                // Turn off request logging unless debug mode is enabled
                request.setLoggingEnabled(loggingEnabled);
                request.setCurlLoggingEnabled(loggingEnabled);
                if (credential.handleResponse(request, response, supportsRetry)) {
                    // If credential decides it can handle it, the return code or message indicated
                    // something specific to authentication, and no backoff is desired.
                    return true;
                } else if (backoffHandler.handleResponse(request, response, supportsRetry)) {
                    // Otherwise, we defer to the judgement of our internal backoff handler.
                    LOG.info("Retrying " + request.getUrl().toString());
                    return true;
                } else {
                    return false;
                }
            }
        });
        request.setIOExceptionHandler(new HttpBackOffIOExceptionHandler(new ExponentialBackOff())
            .setSleeper(sleeper));
    }

    private static void disableHttpTransportLogging() {
        java.util.logging.Logger.getLogger(HttpTransport.class.getName()).setLevel(java.util.logging.Level.OFF);
    }
}
