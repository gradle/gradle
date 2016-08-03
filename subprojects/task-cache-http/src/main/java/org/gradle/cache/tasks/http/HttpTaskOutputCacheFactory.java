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

package org.gradle.cache.tasks.http;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.cache.TaskOutputCache;
import org.gradle.api.internal.tasks.cache.TaskOutputCacheFactory;

import java.net.URI;

public class HttpTaskOutputCacheFactory implements TaskOutputCacheFactory {
    private static final String HTTP_URI_PROPERTY = "org.gradle.cache.tasks.http.uri";

    private final URI root;

    public HttpTaskOutputCacheFactory() {
        String uri = System.getProperty(HTTP_URI_PROPERTY);
        if (uri == null) {
            throw new GradleException(String.format("Must specify HTTP cache backend URI via '%s' system property", HTTP_URI_PROPERTY));
        }
        this.root = URI.create(uri);
    }

    public HttpTaskOutputCacheFactory(URI root) {
        this.root = root;
    }

    @Override
    public TaskOutputCache createCache(StartParameter startParameter) {
        return new HttpTaskOutputCache(root);
    }
}
