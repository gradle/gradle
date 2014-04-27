/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.externalresource;

import org.gradle.internal.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * An immutable resource name. Resources are arranged in a hierarchy. Names may be relative, or absolute with some opaque root resource.
 */
public class ExternalResourceName {
    private final URI uri;

    public ExternalResourceName(URI uri) {
        this.uri = uri;
    }

    public ExternalResourceName(String path) {
        try {
            this.uri = new URI(null, null, path, null);
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public ExternalResourceName(URI parent, String path) {
        try {
            if (parent.getPath() == null) {
                throw new IllegalArgumentException(String.format("Cannot create resource name from non-hierarchical URI '%s'.", parent.toString()));
            }
            String newPath;
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.isEmpty()) {
                newPath = parent.getPath();
            } else if (parent.getPath().endsWith("/")) {
                newPath = parent.getPath() + path;
            } else {
                newPath = parent.getPath() + "/" + path;
            }
            uri = new URI(parent.getScheme(), null, parent.getHost(), parent.getPort(), newPath, null, null);
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public String getDisplayName() {
        return getDecoded();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * Returns a URI that represents this resource.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Returns the 'decoded' name, which is the opaque root + the path of the name.
     */
    public String getDecoded() {
        StringBuilder builder = new StringBuilder();
        if (uri.getScheme() != null) {
            builder.append(uri.getScheme());
            builder.append(":");
        }
        if (uri.getHost() != null) {
            builder.append("//");
            builder.append(uri.getHost());
        }
        if (uri.getPort() > 0) {
            builder.append(":");
            builder.append(uri.getPort());
        }
        if (uri.getPath() != null) {
            builder.append(uri.getPath());
        }
        return builder.toString();
    }

    /**
     * Returns the root name for this name.
     */
    public ExternalResourceName getRoot() {
        try {
            if (uri.getHost() != null) {
                return new ExternalResourceName(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null));
            }
            return new ExternalResourceName(new URI(uri.getScheme(), null, null, -1, uri.getPath().startsWith("/") ? "/" : "", null, null));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Returns the path for this resource. The '/' character is used to separate the elements of the path.
     */
    public String getPath() {
        return uri.getPath();
    }

    /**
     * Resolves the given path relative to this name. The path can be a relative path or an absolute path. The '/' character is used to separate the elements of the path.
     */
    public ExternalResourceName resolve(String path) {
        try {
            String newPath;
            if (path.startsWith("/")) {
                newPath = path;
            } else if (uri.getPath().endsWith("/") || uri.getPath().isEmpty()) {
                newPath = uri.getPath() + path;
            } else {
                newPath = uri.getPath() + "/" + path;
            }
            return new ExternalResourceName(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), newPath, null, null));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(getClass())) {
            return false;
        }
        ExternalResourceName other = (ExternalResourceName) obj;
        return uri.equals(other.uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
