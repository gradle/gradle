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

package org.gradle.caching.http.internal;

import com.google.common.base.Strings;
import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.caching.http.HttpBuildCache;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class DefaultHttpBuildCache extends AbstractBuildCache implements HttpBuildCache {
    private URI url;

    public DefaultHttpBuildCache(String url) {
        this.url = Strings.isNullOrEmpty(url)
            ? null
            : URI.create(url);
    }

    public URI getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        setUrl(URI.create(url));
    }

    @Override
    public void setUrl(URL url) throws URISyntaxException {
        setUrl(url.toURI());
    }

    @Override
    public void setUrl(URI url) {
        this.url = url;
    }
}
