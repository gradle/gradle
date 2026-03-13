/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.services.internal;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.build.BuildIdentity;
import org.jspecify.annotations.Nullable;

/**
 * Details about a service registration.
 */
public class BuildServiceDetails<T extends BuildService<P>, P extends BuildServiceParameters> {
    private final BuildIdentity buildIdentity;
    private final String name;
    private final Class<T> implementationType;
    private final P parameters;
    private final int maxUsages;
    private final boolean resolved;

    public BuildServiceDetails(BuildIdentity buildIdentity, String name, Class<T> implementationType, @Nullable P parameters, @Nullable Integer maxUsages) {
        this.buildIdentity = buildIdentity;
        this.name = name;
        this.implementationType = implementationType;
        this.parameters = parameters;
        this.maxUsages = maxUsages == null ? -1 : maxUsages;
        this.resolved = true;
    }

    public BuildServiceDetails(BuildIdentity buildIdentity, String name, Class<T> implementationType) {
        this.buildIdentity = buildIdentity;
        this.name = name;
        this.implementationType = implementationType;
        this.parameters = null;
        this.maxUsages = -1;
        this.resolved = false;
    }

    public BuildIdentity getBuildIdentity() {
        return buildIdentity;
    }

    public String getName() {
        return name;
    }

    public Class<T> getImplementationType() {
        return implementationType;
    }

    public P getParameters() {
        return parameters;
    }

    public int getMaxUsages() {
        return maxUsages;
    }

    public boolean isResolved() {
        return resolved;
    }
}
