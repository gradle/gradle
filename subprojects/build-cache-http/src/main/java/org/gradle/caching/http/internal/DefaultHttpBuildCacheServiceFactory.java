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

import org.gradle.api.GradleException;
import org.gradle.authentication.Authentication;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.http.HttpBuildCache;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.SslContextFactory;

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

    @Inject
    public DefaultHttpBuildCacheServiceFactory(SslContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
    }

    @Override
    public BuildCacheService createBuildCacheService(HttpBuildCache configuration) {
        URI url = configuration.getUrl();
        if (url == null) {
            throw new IllegalStateException("HTTP build cache has no URL configured");
        }

        Collection<Authentication> authentications = Collections.emptyList();
        if (configuration.getCredentials().getUsername() != null && configuration.getCredentials().getPassword() != null) {
            String userInfo = configuration.getCredentials().getUsername() + ":" + configuration.getCredentials().getPassword();
            try {
                url = new URI(url.getScheme(), userInfo, url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getFragment());
            } catch (URISyntaxException e) {
                throw new GradleException("Invalid credentials", e);
            }
            authentications = Collections.<Authentication>singleton(new AllSchemesAuthentication(configuration.getCredentials()));
        }

        HttpClientHelper httpClientHelper = new HttpClientHelper(new DefaultHttpSettings(authentications, sslContextFactory));
        return new HttpBuildCacheService(httpClientHelper, url);
    }
}
