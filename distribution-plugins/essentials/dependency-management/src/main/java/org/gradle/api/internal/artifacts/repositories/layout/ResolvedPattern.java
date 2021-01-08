/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.layout;

import org.gradle.api.internal.file.FileResolver;

import java.net.URI;

public class ResolvedPattern {
    public final String scheme;
    public final URI baseUri;
    public final String pattern;
    public final String absolutePattern;

    public ResolvedPattern(String rawPattern, FileResolver fileResolver) {
        // get rid of the ivy [] token, as [ ] are not valid URI characters
        int pos = rawPattern.indexOf('[');
        String basePath = pos < 0 ? rawPattern : rawPattern.substring(0, pos);
        this.baseUri = fileResolver.resolveUri(basePath);
        this.pattern = pos < 0 ? "" : rawPattern.substring(pos);
        scheme = baseUri.getScheme().toLowerCase();
        absolutePattern = constructAbsolutePattern(baseUri, pattern);
    }

    public ResolvedPattern(URI baseUri, String pattern) {
        this.baseUri = baseUri;
        this.pattern = pattern;
        scheme = baseUri.getScheme().toLowerCase();
        absolutePattern = constructAbsolutePattern(baseUri, pattern);
    }

    private String constructAbsolutePattern(URI baseUri, String patternPart) {
        String uriPart = baseUri.toString();
        String join = uriPart.endsWith("/") || patternPart.length() == 0 ? "" : "/";
        return uriPart + join + patternPart;
    }
}
