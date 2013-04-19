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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
* By Szczepan Faber on 4/19/13
*/
class DependencyMetadataCache {
    final Map<ModuleVersionSelector, CachedModuleVersionResult> localDescriptors = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    final Map<ModuleVersionSelector, CachedModuleVersionResult> descriptors = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    final Map<ArtifactIdentifier, File> artifacts = new HashMap<ArtifactIdentifier, File>();
}
