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
package org.gradle.api.internal.artifacts.repositories.mirror;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.regex.Pattern;

public final class MirrorDefinition {
    private final String name;
    private final String matchUrlPattern;
    private final Pattern compiledMatcher;
    private final URI mirrorUrl;
    private final @Nullable MirrorCredentialReferences credentials;

    public MirrorDefinition(String name, String matchUrlPattern, URI mirrorUrl, @Nullable MirrorCredentialReferences credentials) {
        this.name = name;
        this.matchUrlPattern = matchUrlPattern;
        this.compiledMatcher = compileGlob(matchUrlPattern);
        this.mirrorUrl = mirrorUrl;
        this.credentials = credentials;
    }

    public String getName() {
        return name;
    }

    public String getMatchUrlPattern() {
        return matchUrlPattern;
    }

    public URI getMirrorUrl() {
        return mirrorUrl;
    }

    @Nullable
    public MirrorCredentialReferences getCredentials() {
        return credentials;
    }

    public boolean matches(URI candidate) {
        return compiledMatcher.matcher(candidate.toString()).matches();
    }

    /**
     * Compiles a simple glob pattern: {@code **} matches any characters (including '/'),
     * {@code *} matches any character except '/'. Everything else is matched literally.
     */
    private static Pattern compileGlob(String glob) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i += 2;
                } else {
                    regex.append("[^/]*");
                    i++;
                }
            } else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
                i++;
            } else {
                regex.append(c);
                i++;
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
