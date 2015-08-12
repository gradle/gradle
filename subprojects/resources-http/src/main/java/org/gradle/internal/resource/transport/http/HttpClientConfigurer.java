/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.transport.http;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.authentication.Authentication;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.authentication.http.DigestAuthentication;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.resource.UriResource;
import org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials;
import org.gradle.internal.resource.transport.http.ntlm.NTLMSchemeFactory;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class HttpClientConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConfigurer.class);

    private final HttpSettings httpSettings;

    public HttpClientConfigurer(HttpSettings httpSettings) {
        this.httpSettings = httpSettings;
    }

    public void configure(DefaultHttpClient httpClient) {
        NTLMSchemeFactory.register(httpClient);
        configureCredentials(httpClient, httpSettings.getAuthenticationSettings());
        configureProxyCredentials(httpClient, httpSettings.getProxySettings());
        configureRetryHandler(httpClient);
        configureUserAgent(httpClient);
    }

    private void configureCredentials(DefaultHttpClient httpClient, Collection<Authentication> authentications) {
        if(authentications.size() > 0) {
            useCredentials(httpClient, AuthScope.ANY_HOST, AuthScope.ANY_PORT, authentications);

            // Use preemptive authorisation if no other authorisation has been established
            httpClient.addRequestInterceptor(new PreemptiveAuth(new BasicScheme(), isPreemptiveEnabled(authentications)), 0);
        }
    }

    private void configureProxyCredentials(DefaultHttpClient httpClient, HttpProxySettings proxySettings) {
        HttpProxySettings.HttpProxy proxy = proxySettings.getProxy();
        if (proxy != null && proxy.credentials != null) {
            useCredentials(httpClient, proxy.host, proxy.port, Collections.singleton(new AllSchemesAuthentication(proxy.credentials)));
        }
    }

    private void useCredentials(DefaultHttpClient httpClient, String host, int port, Collection<? extends Authentication> authentications) {
        Credentials httpCredentials;

        for (Authentication authentication : authentications) {
            String scheme = getAuthScheme(authentication);
            PasswordCredentials credentials = getPasswordCredentials(authentication);

            if (authentication instanceof AllSchemesAuthentication) {
                NTLMCredentials ntlmCredentials = new NTLMCredentials(credentials);
                httpCredentials = new NTCredentials(ntlmCredentials.getUsername(), ntlmCredentials.getPassword(), ntlmCredentials.getWorkstation(), ntlmCredentials.getDomain());
                httpClient.getCredentialsProvider().setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, AuthPolicy.NTLM), httpCredentials);

                LOGGER.debug("Using {} and {} for authenticating against '{}:{}' using {}", new Object[]{credentials, ntlmCredentials, host, port, AuthPolicy.NTLM});
            }

            httpCredentials = new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword());
            httpClient.getCredentialsProvider().setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, scheme), httpCredentials);
            LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", new Object[]{credentials, host, port, scheme});
        }
    }

    private boolean isPreemptiveEnabled(Collection<Authentication> authentications) {
        return CollectionUtils.any(authentications, new Spec<Authentication>() {
            @Override
            public boolean isSatisfiedBy(Authentication element) {
                return element instanceof BasicAuthentication;
            }
        });
    }

    public void configureUserAgent(DefaultHttpClient httpClient) {
        HttpProtocolParams.setUserAgent(httpClient.getParams(), UriResource.getUserAgentString());
    }

    private PasswordCredentials getPasswordCredentials(Authentication authentication) {
        org.gradle.api.credentials.Credentials credentials = ((AuthenticationInternal) authentication).getCredentials();
        if (!(credentials instanceof PasswordCredentials)) {
            throw new IllegalArgumentException(String.format("Credentials must be an instance of: %s", PasswordCredentials.class.getCanonicalName()));
        }

        return Cast.uncheckedCast(credentials);
    }

    private void configureRetryHandler(DefaultHttpClient httpClient) {
        httpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                return false;
            }
        });
    }

    private String getAuthScheme(Authentication authentication) {
        if (authentication instanceof BasicAuthentication) {
            return AuthPolicy.BASIC;
        } else if (authentication instanceof DigestAuthentication) {
            return AuthPolicy.DIGEST;
        } else if (authentication instanceof AllSchemesAuthentication) {
            return AuthScope.ANY_SCHEME;
        } else {
            throw new IllegalArgumentException(String.format("Authentication scheme of '%s' is not supported.", authentication.getClass().getSimpleName()));
        }
    }

    static class PreemptiveAuth implements HttpRequestInterceptor {
        private final AuthScheme authScheme;
        private final boolean alwaysSendAuth;

        PreemptiveAuth(AuthScheme authScheme, boolean alwaysSendAuth) {
            this.authScheme = authScheme;
            this.alwaysSendAuth = alwaysSendAuth;
        }

        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {

            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

            if (authState.getAuthScheme() != null || authState.hasAuthOptions()) {
                return;
            }

            // If no authState has been established and this is a PUT or POST request, add preemptive authorisation
            String requestMethod = request.getRequestLine().getMethod();
            if (alwaysSendAuth || requestMethod.equals(HttpPut.METHOD_NAME) || requestMethod.equals(HttpPost.METHOD_NAME)) {
                CredentialsProvider credentialsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                Credentials credentials = credentialsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (credentials == null) {
                    throw new HttpException("No credentials for preemptive authentication");
                }
                authState.update(authScheme, credentials);
            }
        }
    }
}
