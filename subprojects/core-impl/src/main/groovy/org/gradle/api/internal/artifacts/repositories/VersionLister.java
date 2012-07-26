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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.api.internal.resource.ResourceNotFoundException;

public interface VersionLister {
    /**
     * <p>Returns the VersionList for the given moduleRevisionId, pattern and artifact.</p>
     *
     * @return a VersionList, never returns null.
     * @throws ResourceNotFoundException If information for versions cannot be found.
     * @throws ResourceException If information for versions cannot be loaded.
     */
    VersionList getVersionList(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) throws ResourceNotFoundException, ResourceException;
}
