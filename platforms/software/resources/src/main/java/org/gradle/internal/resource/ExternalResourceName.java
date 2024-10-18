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
import org.gradle.api.NonNullApi;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

/**
 * An immutable resource name. Resources are arranged in a hierarchy. Names may be relative, or absolute with some opaque root resource.
 */
@NonNullApi
public class ExternalResourceName implements Describable {
    @Nullable
    private final String encodedRoot;
    private final String path;
    private final String encodedQuery;

    public ExternalResourceName(URI uri) {
        this(encodeRoot(uri), extractPath(uri), extractQuery(uri));
    }

    public ExternalResourceName(String path) {
        this(null, path, "");
    }

    private ExternalResourceName(@Nullable String encodedRoot, String path) {
        this(encodedRoot, path, "");
    }

    public ExternalResourceName(URI parent, String path) {
        this(encodeRoot(parent), combine(parent, path), "");
    }

    private ExternalResourceName(@Nullable String encodedRoot, String path, String encodedQuery) {
        this.encodedRoot = encodedRoot;
        this.path = path;
        this.encodedQuery = encodedQuery;
    }

    @Override
    public String getDisplayName() {
        return getDisplayable();
    }

    public String getShortDisplayName() {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == -1 ? getDisplayable() : path.substring(lastSlash + 1);
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

    /**
     * Returns the 'displayable' name, which is the opaque root + the encoded path of the name.
     */
    public String getDisplayable() {
        if (encodedRoot == null) {
            return encode(path, false);
        }
        return encodedRoot + encode(path, true);
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
        List<String> parts = new ArrayList<>();
        boolean leadingSlash;
        boolean trailingSlash = path.endsWith("/");
        if (path.startsWith("/")) {
            leadingSlash = true;
        } else {
            leadingSlash = this.path.startsWith("/");
            append(this.path, parts);
        }
        append(path, parts);
        String newPath = join(leadingSlash, trailingSlash, parts);
        return new ExternalResourceName(encodedRoot, newPath);
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

    private static String combine(URI parent, String path) {
        String parentPath = extractPath(parent);
        String childPath = path.startsWith("/") ? path.substring(1) : path;
        if (childPath.length() == 0) {
            return parentPath;
        } else if (parentPath.endsWith("/")) {
            return parentPath + childPath;
        } else {
            return parentPath + "/" + childPath;
        }
    }

    private static boolean isFileOnHost(URI uri) {
        return "file".equals(uri.getScheme()) && uri.getPath().startsWith("//");
    }

    private static String extractPath(URI parent) {
        if (isFileOnHost(parent)) {
            return URI.create(parent.getPath()).getPath();
        }
        return parent.getPath();
    }

    private static String extractQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return "";
        }
        return "?" + rawQuery;
    }

    private static String encodeRoot(URI uri) {
        //based on reversing the operations performed by URI.toString()
        if (uri.getPath() == null) {
            throw new IllegalArgumentException(format("Cannot create resource name from non-hierarchical URI '%s'.", uri));
        }

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

        return builder.toString();
    }

    private static String encode(String path, boolean isPathSeg) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (isLowerCaseChar(ch) ||
                isUpperCaseChar(ch) ||
                isDigit(ch)) {
                builder.append(ch);
            } else if (ch == '/' || ch == '@' || (isPathSeg && ch == ':') || ch == '.' || ch == '-' || ch == '_' || ch == '~'
                || ch == '!' || ch == '$' || ch == '&' || ch == '\'' || ch == '(' || ch == ')' || ch == '*' || ch == '+'
                || ch == ',' || ch == ';' || ch == '=') {
                builder.append(ch);
            } else {
                if (ch <= 0x7F) {
                    escapeByte(ch, builder);
                } else if (ch <= 0x7FF) {
                    escapeByte(0xC0 | ((ch >> 6) & 0x1F), builder);
                    escapeByte(0x80 | (ch & 0x3F), builder);
                } else {
                    escapeByte(0xE0 | ((ch >> 12) & 0x1F), builder);
                    escapeByte(0x80 | ((ch >> 6) & 0x3F), builder);
                    escapeByte(0x80 | (ch & 0x3F), builder);
                }
            }
        }
        return builder.toString();
    }

    private static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private static boolean isUpperCaseChar(char ch) {
        return ch >= 'A' && ch <= 'Z';
    }

    private static boolean isLowerCaseChar(char ch) {
        return ch >= 'a' && ch <= 'z';
    }

    private static void escapeByte(int ch, StringBuilder builder) {
        builder.append('%');
        builder.append(Character.toUpperCase(Character.forDigit(ch >> 4 & 0xFF, 16)));
        builder.append(Character.toUpperCase(Character.forDigit(ch & 0xF, 16)));
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
        int pos = 0;
        while (pos < path.length()) {
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
}
