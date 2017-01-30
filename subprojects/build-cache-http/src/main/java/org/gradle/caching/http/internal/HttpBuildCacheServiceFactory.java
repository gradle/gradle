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

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCacheServiceBuilder;
import org.gradle.caching.configuration.BuildCacheServiceFactory;
import org.gradle.caching.http.HttpBuildCache;

import javax.inject.Inject;
import java.net.URI;

/**
 * Build cache factory for HTTP backends.
 */
public class HttpBuildCacheServiceFactory implements BuildCacheServiceFactory<HttpBuildCache> {
    private static final String HTTP_URI_PROPERTY = "org.gradle.cache.tasks.http.uri";

    private final StartParameter startParameter;

    @Inject
    public HttpBuildCacheServiceFactory(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public Class<HttpBuildCache> getConfigurationType() {
        return HttpBuildCache.class;
    }

    @Override
    public BuildCacheServiceBuilder<? extends HttpBuildCache> createBuilder() {
        String defaultUrl = startParameter.getSystemPropertiesArgs().get(HTTP_URI_PROPERTY);
        if (defaultUrl == null) {
            defaultUrl = System.getProperty(HTTP_URI_PROPERTY);
        }
        final DefaultHttpBuildCache config = new DefaultHttpBuildCache(defaultUrl);

        return new BuildCacheServiceBuilder<HttpBuildCache>() {
            @Override
            public HttpBuildCache getConfiguration() {
                return config;
            }

            @Override
            public BuildCacheService build() {
                URI url = config.getUrl();
                if (url == null) {
                    throw new IllegalStateException("HTTP build cache has no URL configured");
                }
                URI root = URI.create(String.valueOf(url));
                return new HttpBuildCacheService(root);
            }
        };
    }
}
