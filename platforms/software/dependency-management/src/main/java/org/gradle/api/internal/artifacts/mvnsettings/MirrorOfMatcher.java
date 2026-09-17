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
package org.gradle.api.internal.artifacts.mvnsettings;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * Matches a repository against a Maven {@code mirrorOf} pattern.
 *
 * <p>A port of the matching semantics of Maven's own mirror selector
 * ({@code org.eclipse.aether.util.repository.DefaultMirrorSelector}), which is not shipped
 * in the Gradle distribution. The supported grammar is: {@code *}, exact ids, {@code !id}
 * negation, comma-separated lists, {@code external:*} (not localhost, not file based) and
 * {@code external:http:*} (external and plain http). Maven has no partial globs and neither
 * does this matcher. A matching negation short-circuits to "no match"; positive tokens keep
 * scanning so a later negation can override an earlier wildcard ({@code *,!central}).
 */
@NullMarked
final class MirrorOfMatcher {
    private static final String WILDCARD = "*";
    private static final String EXTERNAL_WILDCARD = "external:*";
    private static final String EXTERNAL_HTTP_WILDCARD = "external:http:*";

    private MirrorOfMatcher() {
    }

    static boolean matches(@Nullable String pattern, String repositoryId, URI repositoryUrl) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        if (WILDCARD.equals(pattern) || pattern.equals(repositoryId)) {
            return true;
        }
        boolean result = false;
        for (String token : pattern.split(",")) {
            token = token.trim();
            if (token.length() > 1 && token.startsWith("!")) {
                if (token.substring(1).equals(repositoryId)) {
                    return false;
                }
            } else if (token.equals(repositoryId)) {
                return true;
            } else if (EXTERNAL_WILDCARD.equals(token) && isExternal(repositoryUrl)) {
                result = true;
            } else if (EXTERNAL_HTTP_WILDCARD.equals(token) && isExternalHttp(repositoryUrl)) {
                result = true;
            } else if (WILDCARD.equals(token)) {
                result = true;
            }
        }
        return result;
    }

    private static boolean isExternal(URI url) {
        String host = url.getHost();
        return !("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "file".equalsIgnoreCase(url.getScheme()));
    }

    private static boolean isExternalHttp(URI url) {
        return "http".equalsIgnoreCase(url.getScheme()) && isExternal(url);
    }
}
