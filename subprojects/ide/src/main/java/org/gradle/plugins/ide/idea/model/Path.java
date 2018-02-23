/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;

/**
 * Represents a path in a format as used often in ipr and iml files.
 */
public class Path {

    private final String url;
    private final String relPath;
    private final String canonicalUrl;

    public Path(String url) {
        this(url, url, null);
    }

    public Path(String url, String canonicalUrl, String relPath) {
        this.relPath = relPath;
        this.url = url;
        this.canonicalUrl = canonicalUrl;
    }

    /**
     * The url of the path. Must not be null.
     */
    public String getUrl() {
        return url;
    }

    /**
     * The relative path of the path. Must not be null.
     */
    public String getRelPath() {
        return relPath;
    }

    /**
     * Canonical url.
     */
    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    @Override
    public String toString() {
        return "Path{" + "url='" + url + "\'" + "}";
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Path)) {
            return false;
        }
        Path path = (Path) o;
        return Objects.equal(canonicalUrl, path.canonicalUrl);
    }

    @Override
    public int hashCode() {
        return canonicalUrl.hashCode();
    }
}
