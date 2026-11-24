/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.uri;

import java.net.URI;

/**
 * Renders URIs for messages and logs, masking the user info they may carry
 * since it can hold credentials.
 */
public class UriDisplay {

    private static final String MASK = "***";

    private UriDisplay() {
    }

    /**
     * Returns the given URI with its user info masked.
     */
    public static URI maskUserInfo(URI uri) {
        String text = uri.toString();
        String masked = maskUserInfo(text);
        return masked.equals(text) ? uri : URI.create(masked);
    }

    /**
     * Returns the given text with the user info of its authority masked, when it has the shape of a URI with one.
     * The text is expected to be a valid URI or the leading part of one, such as a URI followed by a path pattern.
     */
    public static String maskUserInfo(String uri) {
        // The authority is introduced by the first '/' of a URI, which has to be doubled and follow a scheme, if any.
        // This also covers a URI nested in another one, such as jar:https://host/a.jar!/b
        int firstSlash = uri.indexOf('/');
        if (firstSlash < 0 || !uri.startsWith("//", firstSlash)) {
            return uri;
        }
        if (firstSlash > 0 && uri.charAt(firstSlash - 1) != ':') {
            return uri;
        }
        int authorityStart = firstSlash + 2;
        int authorityEnd = authorityEnd(uri, authorityStart);
        int userInfoEnd = uri.lastIndexOf('@', authorityEnd - 1);
        if (userInfoEnd < authorityStart) {
            return uri;
        }
        return uri.substring(0, authorityStart) + MASK + uri.substring(userInfoEnd);
    }

    private static int authorityEnd(String uri, int authorityStart) {
        for (int i = authorityStart; i < uri.length(); i++) {
            char ch = uri.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') {
                return i;
            }
        }
        return uri.length();
    }
}
