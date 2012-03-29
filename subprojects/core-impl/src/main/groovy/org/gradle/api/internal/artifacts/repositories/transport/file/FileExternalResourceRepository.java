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
package org.gradle.api.internal.artifacts.repositories.transport.file;

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.file.FileRepository;
import org.gradle.api.internal.artifacts.repositories.ExternalResourceRepository;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.ExternalResourceIvyResourceAdapter;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;

import java.io.File;
import java.io.IOException;

public class FileExternalResourceRepository extends FileRepository implements ExternalResourceRepository {

    public void downloadResource(ExternalResource resource, File destination) throws IOException {
        get(resource.getName(), destination);
    }

    public ExternalResource getResource(String source, LocallyAvailableResourceCandidates localCandidates, CachedExternalResource cached) throws IOException {
        return getResource(source);
    }

    public ExternalResourceMetaData getResourceMetaData(String source) throws IOException {
        Resource resource = getResource(source);
        return resource == null || !resource.exists() ? null : new ExternalResourceIvyResourceAdapter(resource).getMetaData();
    }

    public ExternalResource getResource(String source) throws IOException {
        return new ExternalResourceIvyResourceAdapter(super.getResource(source));
    }
}
