/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Map;

/**
 * Details about a {@link ResolutionAwareRepository}. Used for reporting.
 */
public class RepositoryDetails {

    public final String name;
    public final RepositoryType type;
    public final Map<RepositoryPropertyType, ?> properties;

    RepositoryDetails(String name, RepositoryType type, Map<RepositoryPropertyType, ?> properties) {
        this.name = name;
        this.type = type;
        this.properties = properties;
    }

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum RepositoryPropertyType {

        URL,
        DIRS,
        ARTIFACT_URLS,
        IVY_PATTERNS,
        ARTIFACT_PATTERNS,
        METADATA_SOURCES,
        AUTHENTICATED,
        AUTHENTICATION_SCHEMES,
        LAYOUT_TYPE,
        M2_COMPATIBLE

    }

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum RepositoryType {

        MAVEN,
        IVY,
        FLAT_DIR,
        UNSUPPORTED

    }

}
