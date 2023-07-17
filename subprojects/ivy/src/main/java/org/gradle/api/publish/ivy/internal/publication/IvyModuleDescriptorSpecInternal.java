/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;

import java.util.List;
import java.util.Set;

public interface IvyModuleDescriptorSpecInternal extends IvyModuleDescriptorSpec {

    IvyPublicationIdentity getProjectIdentity();

    SetProperty<IvyConfiguration> getConfigurations();

    Set<IvyArtifact> getArtifacts();

    SetProperty<IvyDependencyInternal> getDependencies();

    SetProperty<IvyExcludeRule> getGlobalExcludes();

    Action<XmlProvider> getXmlAction();

    List<IvyModuleDescriptorAuthor> getAuthors();

    List<IvyModuleDescriptorLicense> getLicenses();

    IvyModuleDescriptorDescription getDescription();

    VersionMappingStrategyInternal getVersionMappingStrategy();

    boolean writeGradleMetadataMarker();
}
