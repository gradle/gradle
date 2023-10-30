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
package org.gradle.api.internal.artifacts.repositories.descriptor;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Map;

/**
 * A non-functional, immutable, description of a {@link ResolutionAwareRepository} at a point in time.
 *
 * See org.gradle.api.internal.artifacts.configurations.ResolveConfigurationResolutionBuildOperationDetails.RepositoryImpl
 */
public abstract class RepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    public enum Type {
        MAVEN,
        IVY,
        FLAT_DIR
    }

    private final String id;
    private final String name;
    private Map<String, ?> properties;

    RepositoryDescriptor(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public abstract Type getType();

    public final Map<String, ?> getProperties() {
        if (properties == null) {
            ImmutableSortedMap.Builder<String, Object> builder = ImmutableSortedMap.naturalOrder();
            addProperties(builder);
            properties = builder.build();
        }

        return properties;
    }

    protected abstract void addProperties(ImmutableSortedMap.Builder<String, Object> builder);

}
