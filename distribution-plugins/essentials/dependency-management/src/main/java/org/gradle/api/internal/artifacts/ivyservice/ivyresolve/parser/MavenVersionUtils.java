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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class MavenVersionUtils {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(.+)-\\d{8}\\.\\d{6}-\\d+");

    public static String inferStatusFromEffectiveVersion(String effectiveVersion) {
        return effectiveVersion != null && effectiveVersion.endsWith("SNAPSHOT") ? "integration" : "release";
    }

    public static String inferStatusFromVersionNumber(String version) {
        return inferStatusFromEffectiveVersion(toEffectiveVersion(version));
    }

    public static String toEffectiveVersion(String version) {
        String effectiveVersion = version;
        if (version != null) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(version);
            if (matcher.matches()) {
                effectiveVersion = matcher.group(1) + "-SNAPSHOT";
            }
        }
        return effectiveVersion;
    }
}
