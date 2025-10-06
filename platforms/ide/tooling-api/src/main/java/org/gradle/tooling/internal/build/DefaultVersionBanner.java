/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.tooling.internal.build;

import org.gradle.tooling.internal.protocol.InternalVersionBanner;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.build.VersionBanner;

import java.io.Serializable;

public class DefaultVersionBanner implements InternalVersionBanner, VersionBanner, Serializable {
    private final BuildIdentifier buildIdentifier;
    private final String versionOutput;

    public DefaultVersionBanner(BuildIdentifier buildIdentifier, String versionOutput) {
        this.buildIdentifier = buildIdentifier;
        this.versionOutput = versionOutput;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public String getVersionOutput() {
        return versionOutput;
    }
}

