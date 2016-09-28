/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.util.Collection;
import java.util.Map;

public interface IvyModulePublishMetadata {
    String IVY_MAVEN_NAMESPACE = "http://ant.apache.org/ivy/maven";
    String IVY_MAVEN_NAMESPACE_PREFIX = "m";

    ModuleDescriptorState getModuleDescriptor();

    ModuleComponentIdentifier getId();

    Map<String, Configuration> getConfigurations();

    Collection<IvyModuleArtifactPublishMetadata> getArtifacts();

    Collection<LocalOriginDependencyMetadata> getDependencies();
}
