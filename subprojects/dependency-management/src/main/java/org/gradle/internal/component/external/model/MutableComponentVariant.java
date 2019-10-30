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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.List;

public interface MutableComponentVariant {

    String getName();

    List<? extends ComponentVariant.File> getFiles();

    void addFile(String name, String uri);

    boolean removeFile(ComponentVariant.File file);

    List<ComponentVariant.Dependency> getDependencies();

    void addDependency(String group, String module, VersionConstraint versionConstraint, List<ExcludeMetadata> excludes, String reason, ImmutableAttributes attributes, List<? extends Capability> requestedCapabilities, boolean endorsing, @Nullable IvyArtifactName artifact);

    List<ComponentVariant.DependencyConstraint> getDependencyConstraints();

    void addDependencyConstraint(String group, String module, VersionConstraint versionConstraint, String reason, ImmutableAttributes attributes);

    List<Capability> getCapabilities();

    void addCapability(String group, String name, String version);

    void addCapability(Capability capability);

    ImmutableAttributes getAttributes();

    void setAttributes(ImmutableAttributes updatedAttributes);

    MutableComponentVariant copy(String variantName, ImmutableAttributes attributes, Capability capability);
}
