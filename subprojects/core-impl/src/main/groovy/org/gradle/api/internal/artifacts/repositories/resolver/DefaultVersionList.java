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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.api.internal.resource.ResourceNotFoundException;

import java.util.HashSet;
import java.util.Set;

public class DefaultVersionList extends AbstractVersionList {
    private final Set<ListedVersion> versions = new HashSet<ListedVersion>();

    public DefaultVersionList() {
    }

    protected void add(ListedVersion newVersion) {
        // Only add when unique
        for (ListedVersion existingVersion : versions) {
            if (existingVersion.getVersion().equals(newVersion.getVersion())) {
                return;
            }
        }
        versions.add(newVersion);
    }

    public void visit(ResourcePattern pattern, Artifact artifact) throws ResourceNotFoundException, ResourceException {
        throw new UnsupportedOperationException();
    }

    public Set<ListedVersion> getVersions() {
        return versions;
    }
}
