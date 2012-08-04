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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.consumer.versioning.VersionDetails;

/**
 * by Szczepan Faber, created at: 1/13/12
 */
public class ConsumerConnectionMetadata {

    private final String displayName;
    private final String version;

    public ConsumerConnectionMetadata(String displayName, String version) {
        this.displayName = displayName;
        this.version = version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public VersionDetails getVersionDetails() {
        if (version == null) {
            throw new UnsupportedOperationException();
        }
        return new VersionDetails(version);
    }
}