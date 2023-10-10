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

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Set;

public interface CachePolicy {
    Expiry versionListExpiry(ModuleIdentifier selector, Set<ModuleVersionIdentifier> moduleVersions, Duration age);

    Expiry missingModuleExpiry(ModuleComponentIdentifier component, Duration age);

    Expiry moduleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age);

    Expiry moduleExpiry(ResolvedModuleVersion resolvedModuleVersion, Duration age, boolean changing);

    Expiry changingModuleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age);

    Expiry moduleArtifactsExpiry(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync);

    Expiry artifactExpiry(ArtifactIdentifier artifactIdentifier, @Nullable File cachedArtifactFile, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync);

    void setOffline();

    void setRefreshDependencies();

}
