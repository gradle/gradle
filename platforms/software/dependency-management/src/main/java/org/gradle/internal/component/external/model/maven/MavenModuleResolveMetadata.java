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

package org.gradle.internal.component.external.model.maven;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Meta-data for a component resolved from a Maven repository.
 */
public interface MavenModuleResolveMetadata extends ModuleComponentResolveMetadata {
    /**
     * {@inheritDoc}
     */
    @Override
    MutableMavenModuleResolveMetadata asMutable();

    @Nonnull String getPackaging();
    boolean isRelocated();
    boolean isPomPackaging();
    boolean isKnownJarPackaging();
    @Nullable
    String getSnapshotTimestamp();

    ImmutableList<MavenDependencyDescriptor> getDependencies();
}
