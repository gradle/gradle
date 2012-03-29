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
package org.gradle.api.internal.externalresource.transport.http;

import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.externalresource.transport.http.ntlm.NTLMCredentials;
import org.gradle.api.internal.externalresource.transport.http.ntlm.NTLMSchemeFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ProxySelector;

public class HttpClientConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientConfigurer.class);

    private final HttpSettings httpSettings;
    private final UsernamePasswordCredentials repositoryCredentials;

    public HttpClientConfigurer(HttpSettings httpSettings) {
        this.httpSettings = httpSettings;
        repositoryCredentials = createRepositoryCredentials(httpSettings.getCredentials());
    }

    private UsernamePasswordCredentials createRepositoryCredentials(PasswordCredentials credentials) {
        if (GUtil.isTrue(credentials.getUsername())) {
            return new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword());
        }
        return null;
    }

    public void configure(DefaultHttpClient httpClient) {
        NTLMSchemeFactory.register(httpClient);
        configureCredentials(httpClient, httpSettings.getCredentials());
        configureProxy(httpClient, httpSettings.getProxySettings());
        configureRetryHandler(httpClient);
    }

    private void configureCredentials(DefaultHttpClient httpClient, PasswordCredentials credentials) {
        if (GUtil.isTrue(credentials.getUsername())) {
            useCredentials(httpClient, credentials, AuthScope.ANY_HOST, AuthScope.ANY_PORT);
        }
    }

    private void configureProxy(DefaultHttpClient httpClient, HttpProxySettings proxySettings) {
        // Use standard JVM proxy settings
        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(httpClient.getConnectionManager().getSchemeRegistry(), ProxySelector.getDefault());
        httpClient.setRoutePlanner(routePlanner);

        HttpProxySettings.HttpProxy proxy = proxySettings.getProxy();
        if (proxy != null && proxy.credentials != null) {
            useCredentials(httpClient, proxy.credentials, proxy.host, proxy.port);
        }
    }

    private void useCredentials(DefaultHttpClient httpClient, PasswordCredentials credentials, String host, int port) {
        Credentials basicCredentials = new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword());
        httpClient.getCredentialsProvider().setCredentials(new AuthScope(host, port), basicCredentials);

        NTLMCredentials ntlmCredentials = new NTLMCredentials(credentials);
        Credentials ntCredentials = new NTCredentials(ntlmCredentials.getUsername(), ntlmCredentials.getPassword(), ntlmCredentials.getWorkstation(), ntlmCredentials.getDomain());
        httpClient.getCredentialsProvider().setCredentials(new AuthScope(host, port, AuthScope.ANY_REALM, AuthPolicy.NTLM), ntCredentials);
        
        LOGGER.debug("Using {} and {} for authenticating against '{}:{}'", new Object[]{credentials, ntlmCredentials, host, port});
    }

    private void configureRetryHandler(DefaultHttpClient httpClient) {
        httpClient.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                return false;
            }
        });
    }

    public void configureMethod(HttpRequest method) {
        method.addHeader("User-Agent", "Gradle/" + GradleVersion.current().getVersion());

        // Do preemptive authentication for basic auth
        if (repositoryCredentials != null) {
            try {
                method.addHeader(new BasicScheme().authenticate(repositoryCredentials, method));
            } catch (AuthenticationException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
