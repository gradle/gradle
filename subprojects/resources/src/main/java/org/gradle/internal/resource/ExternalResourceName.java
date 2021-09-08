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
import org.gradle.api.Describable;
import org.gradle.internal.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * An immutable resource name. Resources are arranged in a hierarchy. Names may be relative, or absolute with some opaque root resource.
 */
public class ExternalResourceName implements Describable {
    private final String encodedRoot;
    private final String path;
    private final String encodedQuery;

    public ExternalResourceName(URI uri) {
        if (uri.getPath() == null) {
            throw new IllegalArgumentException(format("Cannot create resource name from non-hierarchical URI '%s'.", uri.toString()));
        }
        this.encodedRoot = encodeRoot(uri);
        this.path = extractPath(uri);
        this.encodedQuery = extractQuery(uri);
    }

    public ExternalResourceName(String path) {
        encodedRoot = null;
        this.path = path;
        this.encodedQuery = "";
    }

    private ExternalResourceName(String encodedRoot, String path) {
        this.encodedRoot = encodedRoot;
        this.path = path;
        this.encodedQuery = "";
    }

    public ExternalResourceName(URI parent, String path) {
        if (parent.getPath() == null) {
            throw new IllegalArgumentException(format("Cannot create resource name from non-hierarchical URI '%s'.", parent.toString()));
        }
        String newPath;
        String parentPath = extractPath(parent);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() == 0) {
            newPath = parentPath;
        } else if (parentPath.endsWith("/")) {
            newPath = parentPath + path;
        } else {
            newPath = parentPath + "/" + path;
        }
        this.encodedRoot = encodeRoot(parent);
        this.path = newPath;
        this.encodedQuery = "";
    }

    private boolean isFileOnHost(URI uri) {
        return "file".equals(uri.getScheme()) && uri.getPath().startsWith("//");
    }

    private String extractPath(URI parent) {
        if (isFileOnHost(parent)) {
            return URI.create(parent.getPath()).getPath();
        }
        return parent.getPath();
    }

    private String extractQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return "";
        }
        return "?" + rawQuery;
    }

    private String encodeRoot(URI uri) {
        //based on reversing the operations performed by URI.toString()

        StringBuilder builder = new StringBuilder(uri.toString());

        String fragment = uri.getRawFragment();
        if (fragment != null) {
            int index = builder.lastIndexOf("#" + fragment);
            if (index < 0) {
                throw new RuntimeException(format("Can't locate fragment in URI: %s", uri));
            }
            builder.delete(index, builder.length());
        }

        if (uri.isOpaque()) {
            return builder.toString();
        }

        String query = uri.getRawQuery();
        if (query != null) {
            int index = builder.lastIndexOf("?" + query);
            if (index < 0) {
                throw new RuntimeException(format("Can't locate query in URI: %s", uri));
            }
            builder.delete(index, builder.length());
        }

        String path = uri.getRawPath();
        if (path != null && isFileOnHost(uri)) {  //if file URI
            path = URI.create(path).getRawPath(); //remove hostname from path
        }
        if (path != null) {
            int index = builder.lastIndexOf(path);
            if (index < 0) {
                throw new RuntimeException(format("Can't locate path in URI: %s", uri));
            }
            builder.delete(index, builder.length());
        }

        return encode(builder.toString(), true);
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
                return new URI(encode(path, false) + encodedQuery);
            }
            return new URI(encodedRoot + encode(path, true) + encodedQuery);
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
