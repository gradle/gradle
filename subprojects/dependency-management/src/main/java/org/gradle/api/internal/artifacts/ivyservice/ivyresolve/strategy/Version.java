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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

/**
 * A parsed version.
 *
 * This should be synced with {@link org.gradle.util.VersionNumber} and {@link org.gradle.util.GradleVersion} at some point.
 */
public class Version {
    private final String[] parts;

    public Version(String[] parts) {
        this.parts = parts;
    }

    public String[] getParts() {
        return parts;
    }
}
