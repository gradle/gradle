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
package org.gradle.caching.http.internal.httpclient;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.gradle.authentication.Authentication;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.internal.resource.transport.http.HttpClientUtil;
import org.gradle.internal.resource.transport.http.HttpSettings;
import org.gradle.internal.resource.transport.http.HttpTimeoutSettings;
import org.gradle.internal.resource.transport.http.SslContextFactory;

import javax.net.ssl.HostnameVerifier;
import java.net.ProxySelector;
import java.util.Collection;

public class HttpAsyncClientConfigurer {

    private final HttpSettings httpSettings;

    public HttpAsyncClientConfigurer(HttpSettings httpSettings) {
        this.httpSettings = httpSettings;
    }

    public void configure(HttpAsyncClientBuilder builder) {
        SystemDefaultCredentialsProvider credentialsProvider = new SystemDefaultCredentialsProvider();
        configureSslSocketConnectionFactory(builder, httpSettings.getSslContextFactory(), httpSettings.getHostnameVerifier());
        configureAuthSchemeRegistry(builder);
        configureCredentials(builder, credentialsProvider, httpSettings.getAuthenticationSettings());
        configureProxy(builder, credentialsProvider, httpSettings);
        configureUserAgent(builder);
        configureCookieSpecRegistry(builder);
        configureRequestConfig(builder);
        configureSocketConfig(builder);
        configureRedirectStrategy(builder);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        builder.setMaxConnTotal(HttpClientUtil.MAX_HTTP_CONNECTIONS);
        builder.setMaxConnPerRoute(HttpClientUtil.MAX_HTTP_CONNECTIONS);
    }

    private void configureSslSocketConnectionFactory(HttpAsyncClientBuilder builder, SslContextFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        builder.setSSLStrategy(new SSLIOSessionStrategy(sslContextFactory.createSslContext(), HttpClientUtil.supportedTlsVersions().toArray(new String[]{}), null, hostnameVerifier));
    }

    private void configureAuthSchemeRegistry(HttpAsyncClientBuilder builder) {
        builder.setDefaultAuthSchemeRegistry(HttpClientUtil.createAuthSchemeRegistry());
    }

    private void configureCredentials(HttpAsyncClientBuilder builder, CredentialsProvider credentialsProvider, Collection<Authentication> authentications) {
        if (authentications.size() > 0) {
            useCredentials(credentialsProvider, authentications);

            // Use preemptive authorisation if no other authorisation has been established
            builder.addInterceptorFirst(HttpClientUtil.createPreemptiveAuthInterceptor(authentications));
        }
    }

    private void configureProxy(HttpAsyncClientBuilder builder, CredentialsProvider credentialsProvider, HttpSettings httpSettings) {
        HttpClientUtil.configureProxy(credentialsProvider, httpSettings);
        builder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
    }

    private void useCredentials(CredentialsProvider credentialsProvider, Collection<? extends Authentication> authentications) {
        for (Authentication authentication : authentications) {
            HttpClientUtil.useCredentials(credentialsProvider, authentication);
        }
    }

    public void configureUserAgent(HttpAsyncClientBuilder builder) {
        builder.setUserAgent(UriTextResource.getUserAgentString());
    }

    private void configureCookieSpecRegistry(HttpAsyncClientBuilder builder) {
        HttpClientUtil.CookieConfig cookieConfig = HttpClientUtil.createCookieConfig();
        builder.setPublicSuffixMatcher(cookieConfig.publicSuffixMatcher);
        builder.setDefaultCookieSpecRegistry(cookieConfig.cookieSpecRegistry);
    }

    private void configureRequestConfig(HttpAsyncClientBuilder builder) {
        builder.setDefaultRequestConfig(HttpClientUtil.createDefaultRequestConfig(httpSettings));
    }

    private void configureSocketConfig(HttpAsyncClientBuilder builder) {
        HttpTimeoutSettings timeoutSettings = httpSettings.getTimeoutSettings();
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
            .setSoTimeout(timeoutSettings.getSocketTimeoutMs())
            .setSoKeepAlive(true)
            .build();
        builder.setDefaultIOReactorConfig(ioReactorConfig);
    }

    private void configureRedirectStrategy(HttpAsyncClientBuilder builder) {
        builder.setRedirectStrategy(HttpClientUtil.createRedirectStrategy(httpSettings));
    }

}
