/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

public enum Protocol {
    HTTP("http", false, "https"),
    HTTPS("https", true, null),
    FILE("file", true, null);

    public static Protocol fromUrl(URL url) {
        // The getProtocol() method is misnamed, and really returns a scheme, see https://stackoverflow.com/a/47078624/187206
        final String protocol = url.getProtocol();
        return findByPrefix(protocol).orElseThrow(() -> new GradleException(String.format("Unknown protocol: '%s' in URL: '%s'.", protocol, url)));
    }

    public static URL secureProtocol(URL url) {
        final Protocol scheme = Protocol.fromUrl(url);
        final Protocol replacement = scheme.getReplacement();

        try {
            return new URL(url.toString().replaceFirst(scheme.prefix, replacement.prefix));
        } catch (final MalformedURLException e) {
            throw new GradleException(String.format("Error upgrading scheme for URL: '%s'.", url), e);
        }
    }

    private static Optional<Protocol> findByPrefix(String prefix) {
        return Arrays.stream(Protocol.values())
                     .filter(s -> s.prefix.equalsIgnoreCase(prefix))
            .findFirst();
    }

    private final String prefix;
    private final boolean secure;
    @Nullable
    private final String replacement;

    Protocol(String prefix, boolean secure, @Nullable String replacement) {
        this.prefix = prefix;
        this.secure = secure;
        this.replacement = replacement;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean hasReplacement() {
        return replacement != null;
    }

    public Protocol getReplacement() {
        return findByPrefix(replacement).orElseThrow(IllegalStateException::new);
    }

    @Override
    public String toString() {
        return prefix;
    }
}
