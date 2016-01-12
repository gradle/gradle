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
package org.gradle.plugins.ide.idea.model

/**
 * Represents a path in a format as used often in ipr and iml files.
 */
class Path {
    /**
     * The url of the path. Must not be null.
     */
    final String url

    /**
     * The relative path of the path. Must not be null.
     */
    final String relPath

    /**
     * Canonical url.
     */
    final String canonicalUrl

    Path(String url) {
        this(url, url, null)
    }

    Path(String url, String canonicalUrl, String relPath) {
        this.relPath = relPath
        this.url = url
        this.canonicalUrl = canonicalUrl
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (!(o instanceof Path)) {
            return false
        }

        Path path = (Path) o;

        if (canonicalUrl != path.canonicalUrl) {
            return false
        }

        return true;
    }

    int hashCode() {
        return canonicalUrl.hashCode();
    }

    public String toString() {
        return "Path{" +
                "url='" + url + '\'' +
                '}';
    }
}
