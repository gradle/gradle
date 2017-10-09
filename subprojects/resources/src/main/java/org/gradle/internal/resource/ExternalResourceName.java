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

package org.gradle.internal.resource;

import com.google.common.base.Objects;
import org.gradle.internal.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable resource name. Resources are arranged in a hierarchy. Names may be relative, or absolute with some opaque root resource.
 */
public class ExternalResourceName {
    private final String encodedRoot;
    private final String path;

    public ExternalResourceName(URI uri) {
        if (uri.getPath() == null) {
            throw new IllegalArgumentException(String.format("Cannot create resource name from non-hierarchical URI '%s'.", uri.toString()));
        }
        this.encodedRoot = encodeRoot(uri);
        this.path = uri.getPath();
    }

    public ExternalResourceName(String path) {
        encodedRoot = null;
        this.path = path;
    }

    private ExternalResourceName(String encodedRoot, String path) {
        this.encodedRoot = encodedRoot;
        this.path = path;
    }

    public ExternalResourceName(URI parent, String path) {
        if (parent.getPath() == null) {
            throw new IllegalArgumentException(String.format("Cannot create resource name from non-hierarchical URI '%s'.", parent.toString()));
        }
        String newPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() == 0) {
            newPath = parent.getPath();
        } else if (parent.getPath().endsWith("/")) {
            newPath = parent.getPath() + path;
        } else {
            newPath = parent.getPath() + "/" + path;
        }
        this.encodedRoot = encodeRoot(parent);
        this.path = newPath;
    }

    private String encodeRoot(URI uri) {
        StringBuilder builder = new StringBuilder();
        if (uri.getScheme() != null) {
            builder.append(uri.getScheme());
            builder.append(":");

            if (uri.getScheme().equals("file")) {
                if (uri.getPath().startsWith("//")) {
                    builder.append("//");
                }
            }
        }
        if (uri.getHost() != null) {
            builder.append("//");
            builder.append(uri.getHost());
        }
        if (uri.getPort() > 0) {
            builder.append(":");
            builder.append(uri.getPort());
        }
        return builder.toString();
    }

    public String getDisplayName() {
        return getDecoded();
    }

    public String getShortDisplayName() {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == -1 ? getDecoded() : path.substring(lastSlash + 1);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * Returns a URI that represents this resource.
     */
    public URI getUri() {
        try {
            if (encodedRoot == null) {
                return new URI(encode(path, false));
            }
            return new URI(encodedRoot + encode(path, true));
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String encode(String path, boolean isPathSeg) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9') {
                builder.append(ch);
            } else if (ch == '/' || ch == '@' || isPathSeg && ch == ':' || ch == '.' || ch == '-' || ch == '_' || ch == '~'
                || ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+'
                || ch == ',' || ch == ';' || ch == '=') {
                builder.append(ch);
            } else {
                if (ch <= 0x7F) {
                    escapeByte(ch, builder);
                } else if (ch <= 0x7FF) {
                    escapeByte(0xC0 | (ch >> 6) & 0x1F, builder);
                    escapeByte(0x80 | ch & 0x3F, builder);
                } else {
                    escapeByte(0xE0 | (ch >> 12) & 0x1F, builder);
                    escapeByte(0x80 | (ch >> 6) & 0x3F, builder);
                    escapeByte(0x80 | ch & 0x3F, builder);
                }
            }
        }
        return builder.toString();
    }

    private void escapeByte(int ch, StringBuilder builder) {
        builder.append('%');
        builder.append(Character.toUpperCase(Character.forDigit(ch >> 4 & 0xFF, 16)));
        builder.append(Character.toUpperCase(Character.forDigit(ch & 0xF, 16)));
    }

    /**
     * Returns the 'decoded' name, which is the opaque root + the path of the name.
     */
    public String getDecoded() {
        if (encodedRoot == null) {
            return path;
        }
        return encodedRoot + path;
    }

    /**
     * Returns the root name for this name.
     */
    public ExternalResourceName getRoot() {
        return new ExternalResourceName(encodedRoot, path.startsWith("/") ? "/" : "");
    }

    /**
     * Returns the path for this resource. The '/' character is used to separate the elements of the path.
     */
    public String getPath() {
        return path;
    }

    /**
     * Resolves the given path relative to this name. The path can be a relative path or an absolute path. The '/' character is used to separate the elements of the path.
     */
    public ExternalResourceName resolve(String path) {
        List<String> parts = new ArrayList<String>();
        boolean leadingSlash;
        boolean trailingSlash = path.endsWith("/");
        if (path.startsWith("/")) {
            leadingSlash = true;
            append(path, parts);
        } else {
            leadingSlash = this.path.startsWith("/");
            append(this.path, parts);
            append(path, parts);
        }
        String newPath = join(leadingSlash, trailingSlash, parts);
        return new ExternalResourceName(encodedRoot, newPath);
    }

    private String join(boolean leadingSlash, boolean trailingSlash, List<String> parts) {
        if (parts.isEmpty() && leadingSlash) {
            return "/";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0 || leadingSlash) {
                builder.append("/");
            }
            builder.append(part);
        }
        if (trailingSlash) {
            builder.append("/");
        }
        return builder.toString();
    }

    private void append(String path, List<String> parts) {
        for (int pos = 0; pos < path.length();) {
            int end = path.indexOf('/', pos);
            String part;
            if (end < 0) {
                part = path.substring(pos);
                pos = path.length();
            } else {
                part = path.substring(pos, end);
                pos = end + 1;
            }
            if (part.length() == 0 || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                parts.remove(parts.size() - 1);
                continue;
            }
            parts.add(part);
        }
    }

    /**
     * Appends the given text to the end of this path.
     */
    public ExternalResourceName append(String path) {
        return new ExternalResourceName(encodedRoot, this.path + path);
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
        return Objects.equal(encodedRoot, other.encodedRoot) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return (encodedRoot == null ? 0 : encodedRoot.hashCode()) ^ path.hashCode();
    }
}
