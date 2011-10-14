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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ForcedVersion;

/**
 * by Szczepan Faber, created at: 10/11/11
 */
public class DefaultForcedVersion implements ForcedVersion {
    private String group;
    private String name;
    private String version;

    public DefaultForcedVersion(String forcedVersion) {
        assert forcedVersion != null : "forcedVersion cannot be null";
        String[] split = forcedVersion.split(":");
        if (split.length != 3) {
            throw new InvalidDependencyFormat(
                "Invalid format: '" + forcedVersion + "'. Forced version only understands 3-part gav notation,"
                + "e.g. group:artifact:version. Example: org.gradle:gradle-core:1.0-milestone-3");
        }
        group = split[0];
        name = split[1];
        version = split[2];
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public static class InvalidDependencyFormat extends RuntimeException {
        public InvalidDependencyFormat(String message) {
            super(message);
        }
    }
}
