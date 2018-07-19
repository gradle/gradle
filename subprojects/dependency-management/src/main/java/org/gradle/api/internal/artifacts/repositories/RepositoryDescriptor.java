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
 * A non functional description of a {@link ResolutionAwareRepository}, used for reporting.
 */
public class RepositoryDescriptor {

    public final String name;
    public final Type type;
    public final Map<Property, ?> properties;

    RepositoryDescriptor(String name, Type type, Map<Property, ?> properties) {
        this.name = name;
        this.type = type;
        this.properties = properties;
    }

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum Property {

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
    public enum Type {

        MAVEN,
        IVY,
        FLAT_DIR,
        UNSUPPORTED

    }

}
