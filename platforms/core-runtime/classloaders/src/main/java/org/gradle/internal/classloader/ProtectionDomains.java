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

package org.gradle.internal.classloader;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * Resolves the file a class was loaded from via its protection domain's code source.
 */
@NullMarked
public final class ProtectionDomains {

    private ProtectionDomains() {}

    @Nullable
    public static File codeSourceFileOf(@Nullable ProtectionDomain protectionDomain) {
        if (protectionDomain == null) {
            return null;
        }
        CodeSource codeSource = protectionDomain.getCodeSource();
        URL location = codeSource != null ? codeSource.getLocation() : null;
        if (location == null || !"file".equals(location.getProtocol())) {
            return null;
        }
        try {
            return new File(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot parse file URL " + location, e);
        }
    }
}
