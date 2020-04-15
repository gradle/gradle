/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.http.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.authentication.Authentication;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.http.HttpBuildCache;
import org.gradle.caching.http.HttpBuildCacheCredentials;
import org.gradle.internal.authentication.DefaultBasicAuthentication;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.SslContextFactory;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;

/**
 * Build cache factory for HTTP backend.
 */
public class DefaultHttpBuildCacheServiceFactory implements BuildCacheServiceFactory<HttpBuildCache> {

    private final SslContextFactory sslContextFactory;
    private final HttpBuildCacheRequestCustomizer requestCustomizer;

    @Inject
    public DefaultHttpBuildCacheServiceFactory(SslContextFactory sslContextFactory, HttpBuildCacheRequestCustomizer requestCustomizer) {
        this.sslContextFactory = sslContextFactory;
        this.requestCustomizer = requestCustomizer;
    }

    @Override
    public BuildCacheService createBuildCacheService(HttpBuildCache configuration, Describer describer) {
        URI url = configuration.getUrl();
        if (url == null) {
            throw new IllegalStateException("HTTP build cache has no URL configured");
        }
        URI noUserInfoUrl = stripUserInfo(url);

        HttpBuildCacheCredentials credentials = configuration.getCredentials();
        if (!credentialsPresent(credentials) && url.getUserInfo() != null) {
            credentials = extractCredentialsFromUserInfo(url);
        }

        Collection<Authentication> authentications = Collections.emptyList();
        if (credentialsPresent(credentials)) {
            DefaultBasicAuthentication basicAuthentication = new DefaultBasicAuthentication("basic");
            basicAuthentication.setCredentials(credentials);
            basicAuthentication.addHost(url.getHost(), url.getPort());
            authentications = Collections.<Authentication>singleton(basicAuthentication);
        }

        boolean authenticated = !authentications.isEmpty();
        boolean allowUntrustedServer = configuration.isAllowUntrustedServer();
        boolean allowInsecureProtocol = configuration.isAllowInsecureProtocol();

        HttpRedirectVerifier redirectVerifier =
            createRedirectVerifier(noUserInfoUrl, allowInsecureProtocol);

        DefaultHttpSettings.Builder builder = DefaultHttpSettings.builder()
            .withAuthenticationSettings(authentications)
            .followRedirects(false)
            .withRedirectVerifier(redirectVerifier);
        if (allowUntrustedServer) {
            builder.allowUntrustedConnections();
        } else {
            builder.withSslContextFactory(sslContextFactory);
        }
        HttpClientHelper httpClientHelper = new HttpClientHelper(builder.build());

        describer.type("HTTP")
            .config("url", noUserInfoUrl.toASCIIString())
            .config("authenticated", Boolean.toString(authenticated))
            .config("allowUntrustedServer", Boolean.toString(allowUntrustedServer))
            .config("allowInsecureProtocol", Boolean.toString(allowInsecureProtocol));

        return new HttpBuildCacheService(httpClientHelper, noUserInfoUrl, requestCustomizer);
    }

    private HttpRedirectVerifier createRedirectVerifier(URI url, boolean allowInsecureProtocol) {
        return HttpRedirectVerifierFactory
            .create(
                url,
                allowInsecureProtocol,
                () -> DeprecationLogger.deprecate("Using insecure protocols with remote build cache")
                    .withAdvice("Switch remote build cache to a secure protocol (like HTTPS) or allow insecure protocols.")
                    .willBeRemovedInGradle7()
                    .withDslReference(HttpBuildCache.class, "allowInsecureProtocol")
                    .nagUser(),
                redirect -> {
                    throw new IllegalStateException("Redirects are unsupported by the the build cache.");
                });
    }

    @VisibleForTesting
    static HttpBuildCacheCredentials extractCredentialsFromUserInfo(URI url) {
        HttpBuildCacheCredentials credentials = new HttpBuildCacheCredentials();
        String userInfo = url.getUserInfo();
        int indexOfSeparator = userInfo.indexOf(':');
        if (indexOfSeparator > -1) {
            String username = userInfo.substring(0, indexOfSeparator);
            String password = userInfo.substring(indexOfSeparator + 1);
            credentials.setUsername(username);
            credentials.setPassword(password);
        }
        return credentials;
    }

    private static URI stripUserInfo(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new GradleException("Error constructing URL for http build cache", e);
        }
    }

    private static boolean credentialsPresent(HttpBuildCacheCredentials credentials) {
        return credentials.getUsername() != null && credentials.getPassword() != null;
    }

}
