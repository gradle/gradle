/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.util.GradleVersion;

import java.io.IOException;
import java.io.OutputStream;

public class ToolingApiVersionHelper {
    private static final String UNSUPPORTED_MESSAGE = "Support for %s older than %s has been removed.%s You should upgrade %s to version %s or later.";
    private static final String DEPRECATION_WARNING = "Support for %s older than %s is deprecated and will be removed in 5.0.%s You should upgrade your %s.";

    public static final GradleVersion MIN_PROVIDER_VERSION = GradleVersion.version("1.2");

    public static final GradleVersion MIN_LTS_PROVIDER_VERSION = GradleVersion.version("2.6");

    public static UnsupportedVersionException unsupportedProvider() {
        return new UnsupportedVersionException(createUnsupportedMessage(null, MIN_PROVIDER_VERSION, "Gradle"));
    }

    public static void checkProviderVersion(String providerVersion, OutputStream errorStream) {
        checkVersion(errorStream, GradleVersion.version(providerVersion), MIN_PROVIDER_VERSION, MIN_LTS_PROVIDER_VERSION, "Gradle");
    }

    private static void checkVersion(OutputStream errorStream, GradleVersion current, GradleVersion minVersion, GradleVersion minLtsVersion, String description) {
        if (current == null || current.compareTo(minVersion) < 0) {
            throw new UnsupportedVersionException(createUnsupportedMessage(current, minVersion, description));
        }
        if (current.compareTo(minLtsVersion) < 0) {
            try {
                errorStream.write(createDeprecationMessage(current, minLtsVersion, description).getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String createUnsupportedMessage(GradleVersion currentVersion, GradleVersion minVersion, String description) {
        return String.format(UNSUPPORTED_MESSAGE, description, minVersion.getVersion(), createCurrentVersionMessage(currentVersion), description, minVersion.getVersion());
    }

    private static String createCurrentVersionMessage(GradleVersion currentVersion) {
        if (currentVersion == null) {
            return "";
        } else {
            return String.format(" You are currently using %s.", currentVersion.getVersion());
        }
    }

    private static String createDeprecationMessage(GradleVersion currentVersion, GradleVersion minLtsVersion, String description) {
        return String.format(DEPRECATION_WARNING, description, minLtsVersion.getVersion(), createCurrentVersionMessage(currentVersion), description);
    }
}
