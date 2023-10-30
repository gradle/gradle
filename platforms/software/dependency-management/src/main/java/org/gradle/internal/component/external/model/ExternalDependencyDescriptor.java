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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.GraphVariantSelectionResult;

import java.util.Collection;
import java.util.List;

/**
 * Represents dependency information as stored in an external descriptor file (POM or IVY.XML).
 * This information is able to be transformed into a `ModuleDependencyMetadata` instance.
 */
public abstract class ExternalDependencyDescriptor {

    public abstract ModuleComponentSelector getSelector();

    public abstract boolean isOptional();

    public abstract boolean isConstraint();

    public abstract boolean isChanging();

    public abstract boolean isTransitive();

    protected abstract ExternalDependencyDescriptor withRequested(ModuleComponentSelector newRequested);

    protected abstract GraphVariantSelectionResult selectLegacyConfigurations(ComponentIdentifier fromComponent, ConfigurationMetadata fromConfiguration, ComponentGraphResolveState targetComponent);

    public abstract List<ExcludeMetadata> getConfigurationExcludes(Collection<String> configurations);

    public abstract List<IvyArtifactName> getConfigurationArtifacts(ConfigurationMetadata fromConfiguration);
}
