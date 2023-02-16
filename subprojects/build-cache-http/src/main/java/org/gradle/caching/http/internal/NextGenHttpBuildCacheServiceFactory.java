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

import org.gradle.caching.BuildCacheService;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.SslContextFactory;

import javax.inject.Inject;
import java.net.URI;

/**
 * Build cache factory for HTTP backend for NextGen build cache.
 */
public class NextGenHttpBuildCacheServiceFactory extends AbstractHttpBuildCacheServiceFactory {

    @Inject
    public NextGenHttpBuildCacheServiceFactory(SslContextFactory sslContextFactory, HttpBuildCacheRequestCustomizer requestCustomizer, HttpClientHelper.Factory httpClientHelperFactory) {
        super(sslContextFactory, requestCustomizer, httpClientHelperFactory);
    }

    @Override
    BuildCacheService doCreateBuildCacheService(HttpClientHelper httpClientHelper, URI noUserInfoUrl, HttpBuildCacheRequestCustomizer requestCustomizer, boolean useExpectContinue) {
        return new NextGenHttpBuildCacheService(httpClientHelper, noUserInfoUrl, requestCustomizer, useExpectContinue);
    }
}
