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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An _immutable_ view of the variant of a component.
 *
 * TODO - this should replace or merge into VariantResolveMetadata, OutgoingVariant, ConfigurationMetadata
 */
public interface ComponentVariant extends VariantResolveMetadata {
    ImmutableList<? extends Dependency> getDependencies();

    ImmutableList<? extends DependencyConstraint> getDependencyConstraints();

    ImmutableList<? extends File> getFiles();

    interface Dependency {
        String getGroup();

        String getModule();

        VersionConstraint getVersionConstraint();

        ImmutableList<ExcludeMetadata> getExcludes();

        String getReason();

        ImmutableAttributes getAttributes();

        List<Capability> getRequestedCapabilities();

        boolean isEndorsingStrictVersions();

        @Nullable
        IvyArtifactName getDependencyArtifact();

    }

    interface DependencyConstraint {
        String getGroup();

        String getModule();

        VersionConstraint getVersionConstraint();

        String getReason();

        ImmutableAttributes getAttributes();
    }

    interface File {
        String getName();

        String getUri();
    }
}
