/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations.dynamicversion;

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.io.File;
import java.util.Set;

public interface CachePolicy {
    boolean mustRefreshVersionList(ModuleIdentifier selector, Set<ModuleVersionIdentifier> moduleVersions, long ageMillis);

    boolean mustRefreshMissingModule(ModuleComponentIdentifier component, long ageMillis);

    boolean mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis);

    boolean mustRefreshChangingModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis);

    boolean mustRefreshModuleArtifacts(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts, long ageMillis, boolean belongsToChangingModule, boolean moduleDescriptorInSync);

    boolean mustRefreshArtifact(ArtifactIdentifier artifactIdentifier, File cachedArtifactFile, long ageMillis, boolean belongsToChangingModule, boolean moduleDescriptorInSync);
}
